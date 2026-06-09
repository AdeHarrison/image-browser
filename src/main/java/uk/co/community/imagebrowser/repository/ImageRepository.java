package uk.co.community.imagebrowser.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.co.community.imagebrowser.model.ImageRecord;
import uk.co.community.imagebrowser.model.ImageSummary;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ImageRepository {

    private final JdbcTemplate jdbc;

    public ImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------
    // Schema
    // ---------------------------------------------------------------

    @Transactional
    public void createSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS images (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                filename    TEXT    NOT NULL,
                sheet_name  TEXT,
                cell_ref    TEXT,
                mime_type   TEXT    NOT NULL,
                full_image  BLOB    NOT NULL,
                thumbnail   BLOB    NOT NULL,
                width_px    INTEGER,
                height_px   INTEGER,
                created_at  TEXT    DEFAULT (datetime('now'))
            )
        """);
        jdbc.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS images_fts USING fts5(
                filename,
                sheet_name,
                cell_ref,
                tags,
                description,
                content='images',
                content_rowid='id'
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS image_meta (
                image_id    INTEGER PRIMARY KEY REFERENCES images(id),
                tags        TEXT    DEFAULT '',
                description TEXT    DEFAULT ''
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS app_config (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """);
    }

    // ---------------------------------------------------------------
    // Insert
    // ---------------------------------------------------------------

    @Transactional
    public long insert(ImageRecord rec) {
        var keyHolder = new GeneratedKeyHolder();

        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO images
                  (filename, sheet_name, cell_ref, mime_type,
                   full_image, thumbnail, width_px, height_px)
                VALUES (?,?,?,?,?,?,?,?)
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, rec.filename());
            ps.setString(2, rec.sheetName());
            ps.setString(3, rec.cellRef());
            ps.setString(4, rec.mimeType());
            ps.setBytes (5, rec.fullImage());
            ps.setBytes (6, rec.thumbnail());
            ps.setInt   (7, rec.widthPx());
            ps.setInt   (8, rec.heightPx());
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();

        jdbc.update("""
            INSERT INTO image_meta (image_id, tags, description) VALUES (?,?,?)
        """, id, nullSafe(rec.tags()), nullSafe(rec.description()));

        jdbc.update("""
            INSERT INTO images_fts (rowid, filename, sheet_name, cell_ref, tags, description)
            VALUES (?,?,?,?,?,?)
        """, id,
             rec.filename(),
             rec.sheetName(),
             rec.cellRef(),
             nullSafe(rec.tags()),
             nullSafe(rec.description()));

        return id;
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    public List<ImageSummary> search(String query, int offset, int limit) {
        if (query == null || query.isBlank()) {
            return findAll(offset, limit);
        }

        // Append * to last token for prefix matching
        String ftsQuery = Arrays.stream(query.trim().split("\\s+"))
                .collect(Collectors.joining(" ")) + "*";

        return jdbc.query("""
            SELECT i.id, i.filename, i.sheet_name, i.cell_ref,
                   i.width_px, i.height_px,
                   COALESCE(m.tags,'') AS tags,
                   COALESCE(m.description,'') AS description
            FROM images i
            LEFT JOIN image_meta m ON m.image_id = i.id
            JOIN images_fts f ON f.rowid = i.id
            WHERE images_fts MATCH ?
            ORDER BY rank
            LIMIT ? OFFSET ?
        """, this::mapSummary, ftsQuery, limit, offset);
    }

    public List<ImageSummary> findAll(int offset, int limit) {
        return jdbc.query("""
            SELECT i.id, i.filename, i.sheet_name, i.cell_ref,
                   i.width_px, i.height_px,
                   COALESCE(m.tags,'') AS tags,
                   COALESCE(m.description,'') AS description
            FROM images i
            LEFT JOIN image_meta m ON m.image_id = i.id
            ORDER BY i.id
            LIMIT ? OFFSET ?
        """, this::mapSummary, limit, offset);
    }

    // ---------------------------------------------------------------
    // Single image fetch
    // ---------------------------------------------------------------

    public Optional<ImageSummary> findSummaryById(long id) {
        return jdbc.query("""
            SELECT i.id, i.filename, i.sheet_name, i.cell_ref,
                   i.width_px, i.height_px,
                   COALESCE(m.tags,'') AS tags,
                   COALESCE(m.description,'') AS description
            FROM images i
            LEFT JOIN image_meta m ON m.image_id = i.id
            WHERE i.id = ?
        """, this::mapSummary, id).stream().findFirst();
    }

    public Optional<byte[]> findThumbnailById(long id) {
        return jdbc.query(
                "SELECT thumbnail FROM images WHERE id = ?",
                rs -> rs.next() ? Optional.of(rs.getBytes("thumbnail")) : Optional.empty(),
                id);
    }

    public Optional<byte[]> findFullImageById(long id) {
        return jdbc.query(
                "SELECT full_image FROM images WHERE id = ?",
                rs -> rs.next() ? Optional.of(rs.getBytes("full_image")) : Optional.empty(),
                id);
    }

    // ---------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------

    public Optional<Long> findNextId(long currentId) {
        return queryOptionalLong(
                "SELECT id FROM images WHERE id > ? ORDER BY id ASC LIMIT 1", currentId);
    }

    public Optional<Long> findPrevId(long currentId) {
        return queryOptionalLong(
                "SELECT id FROM images WHERE id < ? ORDER BY id DESC LIMIT 1", currentId);
    }

    public Optional<Long> findFirstId() {
        return queryOptionalLong("SELECT id FROM images ORDER BY id ASC LIMIT 1");
    }

    public Optional<Long> findLastId() {
        return queryOptionalLong("SELECT id FROM images ORDER BY id DESC LIMIT 1");
    }

    public List<Long> findAllIds() {
        return jdbc.queryForList("SELECT id FROM images ORDER BY id", Long.class);
    }

    public long count() {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM images", Long.class);
        return result != null ? result : 0L;
    }

    // ---------------------------------------------------------------
    // App config
    // ---------------------------------------------------------------

    public Optional<String> getConfig(String key) {
        return jdbc.query(
                "SELECT value FROM app_config WHERE key = ?",
                rs -> rs.next() ? Optional.of(rs.getString("value")) : Optional.empty(),
                key);
    }

    @Transactional
    public void setConfig(String key, String value) {
        jdbc.update("""
            INSERT INTO app_config (key, value) VALUES (?,?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """, key, value);
    }

    // ---------------------------------------------------------------
    // Clear all (admin reload)
    // ---------------------------------------------------------------

    @Transactional
    public void clearAll() {
        // images_fts is an FTS5 external-content table (content='images'). A plain
        // DELETE makes FTS5 read the indexed columns back from the content table,
        // which fails because tags/description live in image_meta, not images. The
        // 'delete-all' command clears the FTS index without touching content.
        jdbc.execute("INSERT INTO images_fts(images_fts) VALUES('delete-all')");
        jdbc.execute("DELETE FROM image_meta");
        jdbc.execute("DELETE FROM images");
        jdbc.execute("DELETE FROM app_config WHERE key = 'last_updated'");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ImageSummary mapSummary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ImageSummary(
                rs.getLong("id"),
                rs.getString("filename"),
                rs.getString("sheet_name"),
                rs.getString("cell_ref"),
                rs.getInt("width_px"),
                rs.getInt("height_px"),
                rs.getString("tags"),
                rs.getString("description")
        );
    }

    private Optional<Long> queryOptionalLong(String sql, Object... args) {
        List<Long> results = jdbc.queryForList(sql, Long.class, args);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
