package uk.co.community.imagebrowser.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.co.community.imagebrowser.model.AviationImageRecord;
import uk.co.community.imagebrowser.model.AviationImageSummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed store for images imported from the AVIATION sheet.
 * Raw JdbcTemplate SQL, no ORM — matches the rest of the codebase's DB access style.
 */
@Repository
public class AviationImageRepository {

    private static final String SUMMARY_SELECT =
            "SELECT id, category, folder, file_name, description FROM images";

    private final JdbcTemplate jdbc;

    public AviationImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Drops the images table if present, then recreates it empty. created_at is
     * stamped by insert()'s default.
     */
    @Transactional
    public void resetSchema() {
        jdbc.execute("DROP TABLE IF EXISTS images");
        jdbc.execute("""
            CREATE TABLE images (
                id          SERIAL PRIMARY KEY,
                category    TEXT NOT NULL,
                description TEXT NOT NULL,
                folder      TEXT NOT NULL,
                file_name   TEXT NOT NULL,
                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);
    }

    @Transactional
    public void insert(AviationImageRecord rec) {
        jdbc.update("""
            INSERT INTO images (category, description, folder, file_name)
            VALUES (?, ?, ?, ?)
        """, rec.category(), rec.description(), rec.folder(), rec.fileName());
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    /**
     * Partial, case-insensitive match against description; a blank term
     * returns every row.
     */
    public List<AviationImageSummary> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        return jdbc.query(SUMMARY_SELECT + " WHERE description ILIKE ? ORDER BY id",
                this::mapSummary, "%" + escapeLike(term.trim()) + "%");
    }

    public List<AviationImageSummary> findAll() {
        return jdbc.query(SUMMARY_SELECT + " ORDER BY id", this::mapSummary);
    }

    /** One page of every image, ordered by id, for the "browse all" (*) view. */
    public List<AviationImageSummary> findPage(int offset, int limit) {
        return jdbc.query(SUMMARY_SELECT + " ORDER BY id LIMIT ? OFFSET ?",
                this::mapSummary, limit, offset);
    }

    public Optional<AviationImageSummary> findById(long id) {
        return jdbc.query(SUMMARY_SELECT + " WHERE id = ?", this::mapSummary, id)
                .stream().findFirst();
    }

    public long count() {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM images", Long.class);
        return result != null ? result : 0L;
    }

    private AviationImageSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new AviationImageSummary(
                rs.getLong("id"),
                rs.getString("category"),
                rs.getString("folder"),
                rs.getString("file_name"),
                rs.getString("description"));
    }

    /** Escapes ILIKE wildcards so user input is matched literally. */
    private String escapeLike(String term) {
        return term.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }
}
