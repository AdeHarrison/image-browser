# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Locally-hosted image browser for community centres (Spring Boot 4, Java 21). Images are
extracted from embedded pictures in an Excel `.xlsx` workbook, stored in SQLite as BLOBs, and
served to a browser UI. See `README.md` for deployment/operations detail.

## Commands

```bash
mvn clean package          # build fat JAR -> target/image-browser-1.0.0.jar
mvn spring-boot:run        # run in dev (http://localhost:7000)
mvn test                   # unit tests only (Surefire excludes *IT.java / *IntegrationTest.java)
mvn verify                 # unit + integration tests (Failsafe runs *IT.java / *IntegrationTest.java)

# single test class / method
mvn test -Dtest=ImageSearchServiceTest
mvn test -Dtest=ImageSearchServiceTest#methodName
mvn verify -Dit.test=ApplicationIntegrationIT   # single integration test
```

Integration tests (`*IT.java`) run against an in-memory SQLite DB (`jdbc:sqlite::memory:?cache=shared`,
HikariCP pool size 1) configured in `src/test/resources/application-test.properties`.

## Architecture

Request/data flow: **Browser (HTMX) → Controller → Service → ImageRepository → SQLite**, with a
Caffeine cache fronting BLOB reads.

**Startup sequence** (`config/DatabaseInitialiser` `@PostConstruct`):
1. `ImageRepository.createSchema()` — idempotent `CREATE TABLE IF NOT EXISTS`.
2. `SpreadsheetImportService.importIfUpdated()` — imports only if the spreadsheet version changed.
3. `ImageCacheService.schedulePreload()` — async warm-up of the thumbnail cache (`@Async`,
   enabled via `@EnableAsync` on the application class).

**Version-gated import.** The importer reads a timestamp string from cell `A1` of a sheet named
`Metadata` and compares it to the `last_updated` row in the `app_config` table. Equal → import is
skipped (fast startup). Different/absent stored value → `clearAll()` + full reimport + cache
invalidate. Admin "reload" calls `forceImport()`, which reimports unconditionally. **Editing images
in the spreadsheet without changing `Metadata!A1` will NOT trigger a reimport.**

**SQLite schema is maintained manually, not by ORM or FTS triggers.** `images_fts` is an FTS5
external-content table (`content='images'`) but there are **no sync triggers** — `ImageRepository.insert()`
writes to `images`, `image_meta`, and `images_fts` in three explicit statements, and `clearAll()`
deletes from all three plus the `last_updated` config row. If you add a column that should be
searchable, update both the `insert` and the `createSchema` FTS definition. All DB access is raw
`JdbcTemplate` SQL in `ImageRepository` — there is no JPA/Hibernate.

**Search.** `repository.search()` builds an FTS5 `MATCH` query by appending `*` to the token string
for prefix matching, ordered by FTS `rank`. Blank query falls back to `findAll` ordered by `id`.
Navigation (first/prev/next/last) is pure `id`-ordering via `WHERE id >/< ?` queries; there is no
stored sort order beyond insertion order.

**Two-tier image cache (`service/ImageCacheService`).** Despite `@EnableCaching` and the
`spring.cache.*` properties, image BLOBs do NOT use Spring's cache abstraction. This service builds
**two manual Caffeine caches** (thumbnail and full-image) with independent byte-weight budgets,
`expireAfterAccess` TTL, and `softValues()` as a GC safety net. Cache misses load from SQLite via
`ImageRepository`. Always go through `ImageCacheService` for `/thumbnail/{id}` and `/image/{id}/bytes`.

**HTML fragments are Java text blocks, not Thymeleaf.** `ImageController` returns `@ResponseBody`
HTML strings (search cards, modal, viewer) built with `String.formatted(...)` for HTMX swaps;
only the base pages (`index.html`, `admin/*.html`) are Thymeleaf templates. When emitting any
user/data-derived value into these fragments, route it through `escapeHtml(...)` — that is the only
XSS guard. Infinite scroll keys off a page being exactly full (`results.size() == 30`, the
`app.search.page-size` default) emitting a `hx-trigger="revealed"` load-more div.

**`ImageSummary` deliberately omits BLOB fields** so list/search queries never load image bytes;
bytes are fetched only by the dedicated binary endpoints.

**Admin single-session (`admin/AdminSessionManager`).** A single `AtomicReference<String>` holds the
one allowed admin session id; `login`/`logout` are lock-free `compareAndSet`. Auth checks in
`AdminController` require both the `admin` HttpSession attribute AND `isActiveSession(sessionId)`.
The admin password is loaded by `AdminPasswordService` from the external `admin.properties` file
(`app.admin.properties`), defaulting to `changeme` if absent.

## Conventions

- Package root: `uk.co.community.imagebrowser` (`controller` / `service` / `repository` / `model` /
  `admin` / `config`).
- Models are Java `record`s (`ImageRecord`, `ImageSummary`, `AppConfig`, plus inner records like
  `ImportResult`, `NavigationContext`).
- Lombok is a dependency and is excluded from the fat JAR.
- All tunables are `@Value("${app.*}")`-injected with inline defaults that mirror
  `application.properties`; keep the two in sync when adding config.
