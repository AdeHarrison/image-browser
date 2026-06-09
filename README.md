# Community Image Browser

A locally-hosted image browser for community centres. Visitors search and view images extracted from an Excel spreadsheet via a browser-based UI. An admin area manages spreadsheet reloading.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Database | SQLite (single file, zero admin) |
| Connection pool | HikariCP |
| Full-text search | SQLite FTS5 |
| Image cache | Caffeine (in-memory, weight-bounded) |
| Excel reading | Apache POI 5.3 |
| Frontend | Thymeleaf + HTMX |
| Build | Maven (fat JAR via Spring Boot plugin) |

---

## Prerequisites

- **Java 21** (JDK — not JRE)
- **Maven 3.9+**

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

### Development

```bash
mvn spring-boot:run
```

### Production (fat JAR)

```bash
java -Xmx512m -jar target/image-browser-1.0.0.jar
```

Then open: [http://localhost:7000](http://localhost:7000)

The `-Xmx512m` cap is recommended for low-spec PCs. Increase if you have more RAM available.

---

## Deployment Layout

Place these files in the same folder on the target PC:

```
C:\ImageBrowser\
    image-browser-1.0.0.jar
    spreadsheet.xlsx          ← source spreadsheet (see format below)
    admin.properties          ← admin password (see below)
    images.db                 ← created automatically on first run
    start.bat                 ← Windows startup script
```

### start.bat

```bat
@echo off
cd /d C:\ImageBrowser
java -Xmx512m -jar image-browser-1.0.0.jar
```

### Auto-start on Windows boot (Task Scheduler)

1. Open **Task Scheduler** → Create Basic Task
2. Trigger: **When the computer starts**
3. Action: **Start a program** → `C:\ImageBrowser\start.bat`
4. Start in: `C:\ImageBrowser`

### Kiosk mode (optional — hides browser chrome)

```bat
"C:\Program Files\Google\Chrome\Application\chrome.exe" ^
  --kiosk http://localhost:7000 ^
  --no-first-run ^
  --disable-translate
```

---

## Admin Password

Create `admin.properties` next to the JAR:

```properties
admin.password=YourSecurePasswordHere
```

If the file is missing, the default password is `changeme` — always change this before deploying.

---

## Spreadsheet Format

The application reads embedded images from any sheet in the workbook.

### Last Updated (version control)

Add a sheet named **Metadata** with a timestamp in cell **A1**:

```
Sheet: Metadata
A1:    2026-05-22T10:30:00
```

On startup, the app compares this value to the stored version. If it has changed, all images are cleared and reimported. If unchanged, startup is fast (images loaded from cache/database).

**Update A1 every time you add or change images in the spreadsheet.**

### Supported image formats

Raster formats are supported: PNG, JPEG, GIF, BMP, TIFF.

Vector formats (EMF, WMF, SVG) are skipped automatically.

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
| `app.spreadsheet.path` | `spreadsheet.xlsx` | Path to Excel file |
| `app.admin.properties` | `admin.properties` | Path to admin password file |
| `app.thumbnail.max-px` | `128` | Thumbnail longest edge in pixels |
| `app.thumbnail.jpeg-quality` | `0.75` | JPEG compression quality (0.0–1.0) |
| `app.cache.thumbnail-max-bytes` | `15728640` | Thumbnail cache size (15MB) |
| `app.cache.fullimage-max-bytes` | `41943040` | Full image cache size (40MB) |
| `app.cache.expire-minutes` | `15` | Cache TTL after last access |
| `app.preload.enabled` | `true` | Preload thumbnails on startup |
| `app.preload.page-size` | `50` | Preload batch size |
| `app.search.page-size` | `30` | Search results per page |
| `app.db.path` | `images.db` | SQLite database file path |

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
- Force reload from spreadsheet (clears DB and reimports)

---

## Architecture

```
Browser (HTMX)
    │
    ▼
Spring Boot / Tomcat
    │
    ├── ImageController   — search, thumbnails, viewer, modal fragments
    ├── AdminController   — login, logout, reload
    │
    ├── ImageSearchService  — search + navigation logic
    ├── ImageCacheService   — Caffeine L1 cache (thumbnail + full image)
    ├── SpreadsheetImportService — Apache POI → SQLite importer
    ├── ThumbnailService    — JPEG thumbnail generation
    │
    ├── ImageRepository   — all SQLite queries via JdbcTemplate
    └── SQLite (images.db)
            ├── images         — full images + thumbnails as BLOBs
            ├── image_meta     — tags + descriptions
            ├── images_fts     — FTS5 full-text search index
            └── app_config     — last_updated version tracking
```

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

Integration tests use an in-memory SQLite database and are named `*IT.java`.

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
│   │   │   └── SessionConfig.java
│   │   ├── controller/
│   │   │   ├── AdminController.java
│   │   │   └── ImageController.java
│   │   ├── model/
│   │   │   ├── AppConfig.java
│   │   │   ├── ImageRecord.java
│   │   │   └── ImageSummary.java
│   │   ├── repository/
│   │   │   └── ImageRepository.java
│   │   └── service/
│   │       ├── ImageCacheService.java
│   │       ├── ImageSearchService.java
│   │       ├── SpreadsheetImportService.java
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
    ├── java/uk/co/community/imagebrowser/
    │   ├── controller/
    │   │   ├── AdminControllerTest.java
    │   │   └── ImageControllerTest.java
    │   ├── integration/
    │   │   └── ApplicationIntegrationIT.java
    │   ├── repository/
    │   │   └── ImageRepositoryTest.java
    │   └── service/
    │       ├── AdminSessionManagerTest.java
    │       ├── ImageSearchServiceTest.java
    │       ├── SpreadsheetImportServiceTest.java
    │       └── ThumbnailServiceTest.java
    └── resources/
        ├── application-test.properties
        └── test-admin.properties
```

---

## Notes for Developers

- **Cross-reference pattern:** `ImageController` returns HTML fragments directly from Java string templates — no separate `.html` fragment files. This keeps HTMX endpoints simple.
- **No BLOBs in search:** `ImageSummary` deliberately omits byte arrays. BLOBs are only fetched when serving `/thumbnail/{id}` or `/image/{id}/bytes`.
- **Admin single-session:** `AdminSessionManager` uses `AtomicReference.compareAndSet` — thread-safe with no synchronised blocks.
- **Version checking:** Update `Metadata!A1` in the spreadsheet to trigger a reimport on next startup.
- **Cache eviction:** Caffeine evicts by weight (bytes) + LRU + TTL. `softValues()` provides a GC safety net under memory pressure.
