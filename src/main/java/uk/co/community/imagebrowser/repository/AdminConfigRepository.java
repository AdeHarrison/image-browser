package uk.co.community.imagebrowser.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * PostgreSQL-backed key/value store for small pieces of app config — currently
 * just the admin password hash. Raw JdbcTemplate SQL, no ORM — matches the
 * rest of the codebase's DB access style.
 */
@Repository
public class AdminConfigRepository {

    private final JdbcTemplate jdbc;

    public AdminConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the config table if it doesn't exist yet. Idempotent. */
    @Transactional
    public void createSchemaIfNotExists() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS app_config (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """);
    }

    public Optional<String> getValue(String key) {
        return jdbc.query(
                "SELECT value FROM app_config WHERE key = ?",
                rs -> rs.next() ? Optional.of(rs.getString("value")) : Optional.empty(),
                key);
    }

    @Transactional
    public void setValue(String key, String value) {
        jdbc.update("""
            INSERT INTO app_config (key, value) VALUES (?, ?)
            ON CONFLICT (key) DO UPDATE SET value = excluded.value
        """, key, value);
    }
}
