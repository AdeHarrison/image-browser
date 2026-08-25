package uk.co.community.imagebrowser.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.co.community.imagebrowser.model.AviationImageRecord;

/**
 * PostgreSQL-backed store for images imported from the AVIATION sheet.
 * Raw JdbcTemplate SQL, no ORM — matches the rest of the codebase's DB access style.
 */
@Repository
public class AviationImageRepository {

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
}
