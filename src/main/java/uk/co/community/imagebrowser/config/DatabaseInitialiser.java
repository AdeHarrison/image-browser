package uk.co.community.imagebrowser.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.co.community.imagebrowser.repository.AviationImageRepository;

/**
 * Runs once on startup: creates the AVIATION (Postgres) images table if it
 * doesn't exist yet — needed on the very first run, before an admin has
 * triggered an import, so the header's record count (and browsing) don't
 * fail against a missing table. Every subsequent admin import then drops and
 * recreates this same table via AviationImportService.reimport() -> resetSchema().
 */
@Component
public class DatabaseInitialiser {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitialiser.class);

    private final AviationImageRepository aviationRepository;

    public DatabaseInitialiser(AviationImageRepository aviationRepository) {
        this.aviationRepository = aviationRepository;
    }

    @PostConstruct
    public void initialise() {
        log.info("Ensuring AVIATION images table exists...");
        aviationRepository.createSchemaIfNotExists();
        log.info("AVIATION images table ready.");
    }
}
