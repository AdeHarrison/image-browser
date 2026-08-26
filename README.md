# Community Image Browser

A locally-hosted image browser for community centres. Visitors search and view images extracted from an Excel spreadsheet via a browser-based UI. An admin area manages spreadsheet reloading.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Database | PostgreSQL (metadata only — images are stored as files) |
| Connection pool | HikariCP |
| Search | PostgreSQL `ILIKE` (partial, case-insensitive) against description |
| Excel reading | Apache POI 5.5.1 |
| Frontend | Thymeleaf + HTMX |
| Build | Maven (fat JAR via Spring Boot plugin; `spring-boot:build-image` also builds a Docker image) |

---

## Prerequisites

- **Java 21** (JDK — not JRE)
- **Maven 3.9+**
- **PostgreSQL** (e.g. via `docker compose up postgres`, see `docker-compose.yml`)

---

## Building

```bash
mvn clean package
```

This produces a fat JAR at:

```
target/image-browser-1.0.0.jar
```

---

## Running

### Postgres credentials (required — no defaults)

Neither `POSTGRES_USER`/`POSTGRES_PASSWORD` (the Postgres admin/superuser role) nor
`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` (the role the app itself connects
as) have defaults anywhere — not in `docker-compose.yml`, not in `application.properties`.
Every one of them must be supplied explicitly via env vars, or the postgres container /
the app will fail to start. This is deliberate: the app never runs as the Postgres admin role.

**One-time setup on a fresh (empty) `postgres_data` volume:**

```bash
# 1. Create a .env file at the repo root (gitignored) with the admin credentials:
#      POSTGRES_USER=<admin-username>
#      POSTGRES_PASSWORD=<admin-password>

# 2. Start Postgres only, so it bootstraps with those admin credentials
docker compose up -d postgres

# 3. Create the app's own role, confined to its own schema (never the admin role) — run once.
#    Windows:
./scripts/create-app-role.ps1 -AdminUser <admin-username> -AdminPassword <admin-password> -AppUser image_browser_app -AppPassword <app-password>
#    Linux/macOS:
./scripts/create-app-role.sh -u <admin-username> -p <admin-password> -a image_browser_app -w <app-password>
```

The script creates `image_browser_app` (or whatever `-AppUser`/`-a` you choose) with no
superuser/createdb/createrole privileges, owning a dedicated schema (`image_schema` by
default) that it's confined to — it cannot see the `public` schema, any other database, or
any other role. See the script's header comment for full detail on what each step does.

**Local dev** (app runs outside Docker, Postgres inside it):

```bash
docker compose up -d postgres   # if not already running
export SPRING_DATASOURCE_USERNAME=image_browser_app   # or the app role you chose
export SPRING_DATASOURCE_PASSWORD=<app-password>
mvn spring-boot:run
```

(On Windows PowerShell: `$env:SPRING_DATASOURCE_USERNAME = "image_browser_app"`, etc.)

### Production (fat JAR)

```bash
java -Xmx512m -jar target/image-browser-1.0.0.jar
```

Then open: [http://localhost:7000](http://localhost:7000)

The `-Xmx512m` cap is recommended for low-spec PCs. Increase if you have more RAM available.

### Docker

```bash
mvn spring-boot:build-image   # builds image-browser:latest via Cloud Native Buildpacks

# Add to your .env alongside POSTGRES_USER/POSTGRES_PASSWORD:
#   APP_IB_USER=image_browser_app
#   APP_IB_PASSWORD=<app-password>

docker compose up
```

`docker-compose.yml` runs both PostgreSQL and the app, bind-mounting `./data` so the admin
"reload" action can rewrite `data/output` and pick up spreadsheet/image updates under
`data/input` on the host. The app container connects using `APP_IB_USER`/`APP_IB_PASSWORD`
(the scoped role from `scripts/create-app-role`), never `POSTGRES_USER`/`POSTGRES_PASSWORD`.

---

## Admin Password

The admin password is stored as a one-way (BCrypt) hash in the `app_config` table in
PostgreSQL. On first run, no hash exists yet, so the app seeds one for the default password
`changeme` — **always change this before deploying**, via **Admin Panel → Change Admin
Password** (requires the current password).

---

## Spreadsheet Format

The application reads rows from a sheet named **AVIATION** with columns:

```
REF. NO. | LOCATION | DATE | DESCRIPTION/ NOTES | Folder/Page No | Image File Name | ...
```

`DATE` and `DESCRIPTION` are combined into a single searchable description, e.g.
`Mr Hucks publicity photograph (1911)` (the `(date)` suffix is omitted when `DATE` is blank).
`Folder/Page No` and `Image File Name` locate the corresponding image file under
`data/input/AVIATION/<folder>/<fileName>`. A row is skipped (not an error) when description,
folder, or file name is blank.

There is no version-gated import — every admin **Reload** does a full resync: the output
directory is rebuilt from the input directory (`OutputSyncService`), and the `AVIATION` sheet
is re-read into PostgreSQL (`AviationImportService`), dropping and recreating the `images`
table each time.

### Supported image formats

Raster formats are supported: PNG, JPEG, GIF, BMP, TIFF.

Vector formats (EMF, WMF, SVG) are skipped automatically (no thumbnail generated).

---

## LAN Deployment

The app binds to all network interfaces by default. To expose it on a LAN:

1. Give the host PC a **static IP** (e.g. `192.168.1.50`)
2. Allow port 7000 inbound through **Windows Firewall**
3. Other PCs on the LAN access it via: `http://192.168.1.50:7000`

No code changes are needed. For higher concurrency, HikariCP (already included) handles multiple simultaneous connections automatically.

---

## Configuration

All configuration is in `application.properties` (or override via environment variables / command-line args):

| Property | Default | Description |
|---|---|---|
| `server.port` | `7000` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/image_browser` | PostgreSQL connection URL |
| `app.spreadsheet.path` | `data/input/Archive_Index_Numbers_Current.xlsx` | Path to Excel file |
| `app.data.output-dir` | `data/output` | Where synced images/thumbnails are written |
| `app.browse.page-size` | `60` | Images per page in "browse all" (`*`) mode |
| `app.thumbnail.max-px` | `128` | Thumbnail longest edge in pixels |
| `app.thumbnail.jpeg-quality` | `0.75` | JPEG compression quality (0.0–1.0) |

Override example:

```bash
java -Xmx512m \
  -Dserver.port=8080 \
  -Dapp.thumbnail.max-px=200 \
  -jar image-browser-1.0.0.jar
```

---

## Admin Area

Navigate to [http://localhost:7000/admin/login](http://localhost:7000/admin/login)

**Features:**
- Password-protected login
- Only one admin session allowed at a time
- Sessions expire after 30 minutes of inactivity
- Reload (resync images from spreadsheet + reimport into PostgreSQL), with a live progress bar

---

## Running Tests

### Unit tests only

```bash
mvn test
```

### Unit + integration tests

```bash
mvn verify
```

Integration tests are named `*IT.java`.

---

## Project Structure

```
src/
├── main/
│   ├── java/uk/co/community/imagebrowser/
│   │   ├── ImageBrowserApplication.java
│   │   ├── admin/
│   │   │   ├── AdminPasswordService.java
│   │   │   └── AdminSessionManager.java
│   │   ├── config/
│   │   │   ├── DatabaseInitialiser.java
│   │   │   ├── SessionConfig.java
│   │   │   └── SpreadsheetAvailabilityCheck.java
│   │   ├── controller/
│   │   │   ├── AdminController.java
│   │   │   └── ImageController.java
│   │   ├── model/
│   │   │   ├── AviationImageRecord.java
│   │   │   └── AviationImageSummary.java
│   │   ├── repository/
│   │   │   ├── AdminConfigRepository.java
│   │   │   └── AviationImageRepository.java
│   │   └── service/
│   │       ├── AdminReloadService.java
│   │       ├── AviationImageService.java
│   │       ├── AviationImportService.java
│   │       ├── ImportProgressService.java
│   │       ├── OutputSyncService.java
│   │       └── ThumbnailService.java
│   └── resources/
│       ├── application.properties
│       ├── static/
│       │   ├── css/main.css
│       │   └── js/app.js
│       └── templates/
│           ├── index.html
│           └── admin/
│               ├── login.html
│               └── panel.html
└── test/
    └── java/uk/co/community/imagebrowser/
        ├── admin/
        │   └── AdminPasswordServiceTest.java
        ├── config/
        │   ├── DatabaseInitialiserTest.java
        │   └── SessionConfigTest.java
        ├── controller/
        │   ├── AdminControllerTest.java
        │   └── ImageControllerTest.java
        └── service/
            ├── AdminSessionManagerTest.java
            ├── AviationImportServiceTest.java
            └── ThumbnailServiceTest.java
```

---

## Notes for Developers

- **Cross-reference pattern:** `ImageController` returns HTML fragments directly from Java string templates — no separate `.html` fragment files. This keeps HTMX endpoints simple.
- **No BLOBs anywhere:** images live as files under `data/output`; PostgreSQL only stores metadata (`category`, `folder`, `file_name`, `date`, `description`).
- **Admin single-session:** `AdminSessionManager` uses `AtomicReference.compareAndSet` — thread-safe with no synchronised blocks.
- **Full resync on reload:** there is no version check — every admin "Reload" rebuilds `data/output` from `data/input` and drops/recreates the PostgreSQL `images` table from the `AVIATION` sheet.
