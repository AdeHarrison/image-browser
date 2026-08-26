# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Locally-hosted image browser for community centres (Spring Boot 4, Java 21). Images live as
files on disk (synced from an Excel `.xlsx` workbook's input folder), with only metadata —
category, folder, file name, date, description — stored in PostgreSQL. See `README.md` for
deployment/operations detail.

## Commands

```bash
mvn clean package          # build fat JAR -> target/image-browser-1.0.0.jar
mvn spring-boot:run        # run in dev (http://localhost:7000) — needs PostgreSQL, see docker-compose.yml
mvn test                   # unit tests only (Surefire excludes *IT.java / *IntegrationTest.java)
mvn verify                 # unit + integration tests (Failsafe runs *IT.java / *IntegrationTest.java)

# single test class / method
mvn test -Dtest=AviationImportServiceTest
mvn test -Dtest=AviationImportServiceTest#methodName
```

There are currently no `*IT.java` integration tests in the repo (the old one ran against an
in-memory embedded DB from a since-removed architecture — see below); `mvn verify` will simply
find none to run via Failsafe.

## Architecture

Request/data flow: **Browser (HTMX) → `ImageController` → `AviationImageService` →
`AviationImageRepository`** (PostgreSQL, metadata only) **+ direct filesystem reads** under
`data/output/{category}/{folder}/` for thumbnail/full-image bytes. There is no in-process image
cache — each `/thumbnail/{id}` and `/image/{id}` request reads straight off disk (the OS page
cache is relied on for hot paths).

**Startup** — three independent `@PostConstruct` components, order between them not guaranteed:
1. `config/DatabaseInitialiser` — `AviationImageRepository.createSchemaIfNotExists()`, idempotent,
   so the `images` table (and a count of 0) exists even before an admin has ever imported.
2. `admin/AdminPasswordService.load()` — calls `AdminConfigRepository.createSchemaIfNotExists()`
   itself (idempotent) rather than relying on startup order, then reads the BCrypt hash from the
   `app_config` table (key `admin_password_hash`), seeding a hash of the default password
   `changeme` on first run.
3. `config/SpreadsheetAvailabilityCheck` — confirms `app.spreadsheet.path` exists and contains an
   `AVIATION` sheet; throws (failing startup) if not. This is a hard requirement, not advisory.

**No version-gated import.** Unlike a typical "only reimport if changed" design, every admin
**Reload** (`POST /admin/reload`, `AdminReloadService.runReload()`, run `@Async` so the request
returns immediately and the UI polls `GET /admin/reload/progress` via `ImportProgressService`)
does a full resync, in order:
1. `OutputSyncService.sync()` — wipes `data/output`, then copies `data/input` into it: top-level
   files (the spreadsheet) as-is, nested images as `FULL-{name}` plus a generated `THUMB-{name}`
   thumbnail (`ThumbnailService`). No DB writes.
2. `AviationImportService.reimport()` — reads the `AVIATION` sheet (columns: `REF. NO.`,
   `LOCATION`, `DATE`, `DESCRIPTION/ NOTES`, `Folder/Page No`, `Image File Name`), drops and
   recreates the PostgreSQL `images` table (`AviationImageRepository.resetSchema()`), and inserts
   one row per valid image (a row is skipped, not an error, when description/folder/file name is
   blank). `DATE` and `DESCRIPTION` are combined into one searchable string, e.g.
   `"Mr Hucks publicity photograph (1911)"` (the `(date)` suffix omitted when `DATE` is blank).

**Search.** `AviationImageRepository.search(term)` does a partial, case-insensitive `ILIKE
'%term%'` match against `description` (wildcard characters in the input are escaped so they're
matched literally), ordered by `id`. A blank term returns every row. All DB access is raw
`JdbcTemplate` SQL — there is no JPA/Hibernate, and no FTS/trigram index.

**HTML fragments are Java text blocks, not Thymeleaf.** `ImageController` returns `@ResponseBody`
HTML strings (search/browse cards, browse-nav) built with `String.formatted(...)` for HTMX swaps;
only the base pages (`index.html`, `admin/*.html`) are Thymeleaf templates. The full-image
viewer/modal is client-side JS in `index.html` hitting `/image/{id}` directly — there's no
server-rendered modal fragment. When emitting any user/data-derived value into a fragment, route
it through `escapeHtml(...)` — that is the only XSS guard.

**`AviationImageSummary` deliberately omits any image bytes** — just enough to locate the file on
disk (`category`/`folder`/`fileName`) and render a card. Binary bytes are only read by
`AviationImageService.getThumbnail()`/`getFullImage()`, backing the dedicated `/thumbnail/{id}`
and `/image/{id}` endpoints.

**Admin single-session (`admin/AdminSessionManager`).** A single `AtomicReference<String>` holds
the one allowed admin session id; `login`/`logout` are lock-free `compareAndSet`. Auth checks in
`AdminController.isAdmin()` require both the `admin` HttpSession attribute AND
`isActiveSession(sessionId)`. Admins change the password via `POST /admin/change-password`
(`AdminController.changePassword`), which verifies the current password, checks the new password
against `confirmPassword`, and enforces `AdminPasswordService.MIN_PASSWORD_LENGTH` via
`setPassword()`.

**`TEST_MODE` env var bypasses admin auth entirely** (`AdminController.isAdmin()` short-circuits
to `true`, bound via `@Value("${test.mode:false}")` from the env var by Spring's relaxed
binding). For automated testing only — must never be set in a deployed/production environment.
Logged as a startup warning when enabled.

## Conventions

- Package root: `uk.co.community.imagebrowser` (`controller` / `service` / `repository` / `model` /
  `admin` / `config`).
- Models are Java `record`s (`AviationImageRecord`, `AviationImageSummary`, plus inner records
  like `ImportProgressService.Snapshot`).
- Lombok is a dependency and is excluded from the fat JAR.
- All tunables are `@Value("${app.*}")`-injected with inline defaults that mirror
  `application.properties`; keep the two in sync when adding config.
