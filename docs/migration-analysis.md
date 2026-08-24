# Migration Analysis & Planning Notes

_Generated 2026-08-24_

---

## Project Analysis

### Stack

| Layer | Technology |
|---|---|
| Runtime | Spring Boot 4 / Java 21 |
| Database | SQLite (raw `JdbcTemplate`, no ORM) |
| Spreadsheet | Apache POI 5.5.1 (`poi-ooxml`) |
| UI | HTMX + Thymeleaf (base pages only) |
| Cache | Caffeine (two manual caches, not Spring abstraction) |
| Auth | BCrypt hash in `app_config`, single-session `AtomicReference` |

---

### Data Model

**SQLite schema** (manually maintained — no ORM, no migration tool):

```
images        — id, filename, sheet_name, cell_ref, mime_type, full_image(BLOB), thumbnail(BLOB), width_px, height_px, created_at
image_meta    — image_id (FK→images), tags, description
images_fts    — FTS5 virtual table: filename, sheet_name, cell_ref, tags, description
app_config    — key/value: last_updated, admin_password_hash
```

**Java models:**
- `ImageRecord` — full record including BLOBs (only used during import)
- `ImageSummary` — lightweight (no BLOBs), used for search results and grid display
- `AppConfig` — constants for `app_config` keys

---

### Spreadsheet Import — How It Works Now

`SpreadsheetImportService` is the critical class. Here's exactly what it does:

1. **Version check** — reads `Metadata` sheet, cell **B1** (string, ISO `LocalDateTime` format, e.g. `2026-05-22T10:30:00`). If the DB has a stored version that is >= this value, import is skipped entirely.

2. **Iterates ALL sheets** (including Metadata) looking for `XSSFDrawing` objects — skips sheets with no drawings.

3. **Per picture** — extracts `XSSFPicture`, gets raw bytes, generates a JPEG thumbnail, creates an `ImageRecord` with:
   - `filename` → auto-generated: `sheet_{sheetName}_img_{index}.{ext}` (not human-readable)
   - `sheet_name` → the Excel sheet tab name
   - `cell_ref` → anchor position `R{row1}C{col1}` (top-left corner of the image shape)
   - `tags` → empty string
   - `description` → empty string
   - `width_px`, `height_px` → stored as **0** (a known gap — the actual dimensions after scaling are not fed back)

4. **Skips** vector formats (emf, wmf, svg).

5. **No cell text is read** from any sheet — only the embedded picture BLOBs.

---

### Search

FTS5 `MATCH` query with prefix matching (`*` appended to last token). Searches across: `filename`, `sheet_name`, `cell_ref`, `tags`, `description`. Since `tags` and `description` are always empty at import time, search currently only works on the auto-generated filename and the sheet tab name.

---

### Key Gaps Relevant to Spreadsheet Changes

| Gap | Location | Impact |
|---|---|---|
| No cell text read during import | `SpreadsheetImportService:importImages()` | Tags/descriptions can't be populated from spreadsheet data |
| `width_px`/`height_px` stored as 0 | `SpreadsheetImportService:152-153` | Dimensions never stored, even though thumbnail creation knows them |
| Filename is synthetic, not human-readable | `SpreadsheetImportService:154` | Makes search/identification hard |
| Only B1 of Metadata sheet is read | `SpreadsheetImportService:readLastUpdated()` | Other metadata columns on that sheet are ignored |
| `Metadata` sheet is also iterated for images | `importImages()` — no exclusion | If you put a picture on the Metadata sheet it will be imported |

---

### What Changing the Spreadsheet Structure Would Touch

If you want to add **per-image metadata** (title, description, tags, category) in spreadsheet cells alongside the images, changes would be needed in:

1. **`SpreadsheetImportService.importImages()`** — read cell values near each image's anchor position
2. **`ImageRecord`** — add any new fields (e.g. `title`, `category`)
3. **`ImageSummary`** — add matching display fields
4. **`ImageRepository.createSchema()`** — add columns to `images` or `image_meta` tables and update the FTS5 virtual table definition
5. **`ImageRepository.insert()`** — write the new fields (both to `images`/`image_meta` and to `images_fts`)
6. **`ImageController`** — expose new fields in the modal/viewer HTML fragments
7. **Test spreadsheet** (`src/test/resources/test-spreadsheet.xlsx`) — update to match new structure
8. **`SpreadsheetImportServiceTest`** — update tests

If you only **rename sheets** or **reorganise images between sheets**, the code handles that automatically — `sheet_name` just changes value.

If you change the **Metadata sheet** format (e.g. move the timestamp to a different cell, or add more config rows), only `readLastUpdated()` needs touching.

---

## SQLite at Scale — Assessment

### BLOB Storage: SQLite is a poor choice at scale

The current design stores full images AND thumbnails as BLOBs in the database. The numbers:

| Images | Avg full JPEG | Thumbnails | DB file size |
|---|---|---|---|
| 10,000 | 500 KB | 10 KB | ~5 GB |
| 100,000 | 500 KB | 10 KB | ~50 GB |

A single 50 GB SQLite file has serious practical problems:
- **Corruption risk** — one corrupt file loses everything
- **Backup complexity** — you can't copy a live SQLite file safely without WAL tricks
- **Atomic imports** — the current `clearAll()` + reimport pattern holds a write lock across the entire operation; at 100k images that could take minutes with everything blocked
- **OS/filesystem limits** — many systems struggle with multi-GB single files

**The standard solution is filesystem storage for the binary content** — store images as files in a directory tree and put only the file path in the database. SQLite then stores only metadata (kilobytes per row), scales to 100k+ rows trivially, and images are individually accessible, deletable, and backupable.

---

### Search: FTS5 Does NOT Do "Any Position" Matching

FTS5 is a **tokeniser-based** full-text engine — it splits text into words at whitespace/punctuation, indexes those tokens, and matches on word prefixes.

| Search term | Field value | FTS5 result |
|---|---|---|
| `party` | `"street party 2024"` | **Match** (token) |
| `par` | `"street party 2024"` | **Match** (prefix `*`) |
| `arty` | `"street party 2024"` | **No match** (mid-word) |
| `1234` | `"IMG_1234_hall"` | **No match** (underscore splits tokens differently) |
| `hall` | `"IMG_1234_hall"` | **Match** (token after split) |

For true "any position, case-insensitive" substring search, options are:

| Option | How | Trade-off |
|---|---|---|
| `LIKE '%term%'` queries | Replace FTS5 with `LIKE` | Works but full table scan — slow at 100k rows |
| SQLite trigram | No native support | Not available without custom extension |
| **PostgreSQL + `pg_trgm`** | `CREATE INDEX ... USING GIN (col gin_trgm_ops)` | True substring search, fast at any scale |
| Meilisearch / Typesense | Separate search process | Overkill for a local community app |

---

## Filesystem vs BLOB Performance

### Cache hits — identical

Once a thumbnail or full image is loaded into the Caffeine cache it's a pure in-memory `byte[]` lookup regardless of where it originally came from.

### Cache misses — filesystem wins

Current BLOB path:
```
Caffeine miss → JDBC query → SQLite B-tree traversal
              → overflow page reads (large BLOBs span many 4KB pages)
              → ResultSet deserialise → byte[] allocated → back to Caffeine
```

Filesystem path:
```
Caffeine miss → File.read() → OS page cache (warm) or SSD read (cold)
              → byte[] allocated → back to Caffeine
```

The OS page cache is typically hundreds of MB to several GB — far larger than SQLite's 32MB `cache_size` pragma. It survives app restarts. File reads avoid B-tree traversal and overflow-page indirection.

### Current Caffeine budget

```
app.cache.thumbnail-max-bytes=15728640    # 15 MB ≈ ~1,900 thumbnails at 8KB each
app.cache.fullimage-max-bytes=41943040   # 40 MB
```

At 10,000 images: ~81% miss rate on a cold scroll. At 100,000 images: essentially every thumbnail beyond the first page is a cache miss — source performance matters a lot.

### Zero-copy opportunity

With filesystem storage, Spring/Tomcat can use `sendfile()` — bytes go from disk to network socket without copying into JVM heap. With BLOBs you must load into a `byte[]`, then write to the response stream — two copies through heap, more GC pressure.

| | SQLite BLOB | Filesystem |
|---|---|---|
| Caffeine hit | Same | Same |
| Caffeine miss | B-tree + overflow pages | OS page cache or direct file read |
| Very large scale (100k) | Many misses, slow | OS cache handles it well |
| GC pressure | High (`byte[]` per BLOB load) | Lower (sendfile possible) |
| Backup/restore | Whole DB file | Individual files or rsync |

---

## Recommended Migration Order

### Phase 1 — Design (no code yet)

**1. Define the new spreadsheet structure**
This gates everything else. Questions to settle:
- What metadata lives in cells alongside/near images? (title, description, tags, category, date, location?)
- How are images identified/anchored to their metadata? (same row? named cell? separate lookup sheet?)
- Does the Metadata sheet change format?

**2. Decide SQLite vs PostgreSQL**
Do this before writing any new schema. Given 100k+ images and substring search requirement, PostgreSQL is the better long-term answer. Pick one now.

**3. Design the new schema on paper**
Once fields from step 1 and engine from step 2 are known, design the final table columns, indexes, and FTS/trigram approach.

---

### Phase 2 — Infrastructure (the foundation everything else sits on)

**4. Switch to filesystem storage**
Contained, well-understood change:
- `SpreadsheetImportService` writes files instead of BLOBs
- `ImageRepository` stores paths instead of BLOBs
- `ImageCacheService` reads files instead of DB BLOBs
- Schema loses `full_image`/`thumbnail` BLOB columns

Do this before touching the importer or schema — the later importer rewrite then never deals with BLOBs at all.

**5. If PostgreSQL: migrate the engine**
Schema is almost pure standard SQL — `AUTOINCREMENT` → `SERIAL`, WAL pragma goes away, FTS5 → `pg_trgm`. Do this as a clean step before adding new schema columns.

---

### Phase 3 — Schema and data model

**6. Add new columns from the spreadsheet redesign**
Add new fields to `images`/`image_meta`, update FTS index definition, update `ImageRecord` and `ImageSummary` records, update `ImageRepository.insert()`, `mapSummary()`, and `createSchema()`.

---

### Phase 4 — Importer rewrite

**7. Rewrite `SpreadsheetImportService.importImages()`**
Informed by the final spreadsheet structure, final schema, and filesystem storage. Write it once correctly.

**8. Update the test spreadsheet** (`src/test/resources/test-spreadsheet.xlsx`)
Must mirror the new format. Do this alongside step 7 — it drives the importer tests.

---

### Phase 5 — UI and search

**9. Update search** (`ImageRepository.search()`)
Update the query to use new fields and new FTS/trigram approach.

**10. Update controllers and HTML fragments**
`ImageController.modal()` and `viewer()` — expose new metadata fields.

---

### Phase 6 — Tests

**11. Update/add tests throughout**
`SpreadsheetImportServiceTest`, `ImageRepositoryTest`, `ApplicationIntegrationIT`. Tests last because they verify the final design.

---

### Critical Dependency Chain

```
New spreadsheet structure
        ↓
    Schema design
        ↓
Filesystem storage + DB engine
        ↓
  Repository layer
        ↓
  Importer rewrite
        ↓
  Search + UI
        ↓
     Tests
```
