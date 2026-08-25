package uk.co.community.imagebrowser.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.co.community.imagebrowser.repository.AviationImageRepository;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.service.SpreadsheetImportService;

/**
 * Runs once on startup:
 *   1. Creates the SQLite schema (idempotent — IF NOT EXISTS)
 *   2. Checks the spreadsheet last-updated value and imports if changed
 *   3. Creates the AVIATION (Postgres) images table if it doesn't exist yet —
 *      needed on the very first run, before an admin has triggered an import,
 *      so the header's record count (and browsing) don't fail against a
 *      missing table. Every subsequent admin import then drops and recreates
 *      this same table via AviationImportService.reimport() -> resetSchema().
 */
@Component
public class DatabaseInitialiser {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitialiser.class);

    private final ImageRepository          repository;
    private final SpreadsheetImportService importService;
    private final AviationImageRepository  aviationRepository;

    public DatabaseInitialiser(ImageRepository repository,
                                SpreadsheetImportService importService,
                                AviationImageRepository aviationRepository) {
        this.repository         = repository;
        this.importService      = importService;
        this.aviationRepository = aviationRepository;
    }

    @PostConstruct
    public void initialise() {
        // DB-backed import is disabled for now — only the file-based output sync
        // (OutputSyncService) runs currently, while the importer/schema rework for
        // the new spreadsheet format is in progress. Re-enable once that's ready.
        //
        // log.info("Initialising database schema...");
        // repository.createSchema();
        // log.info("Schema ready.");
        //
        // log.info("Checking spreadsheet for updates...");
        // try {
        //     var result = importService.importIfUpdated();
        //     log.info("Spreadsheet check: status={} message='{}'",
        //             result.status(), result.message());
        // } catch (Exception e) {
        //     log.error("Spreadsheet import failed on startup: {}", e.getMessage(), e);
        // }
        log.info("Database initialisation skipped for now (DB-backed import disabled).");

        log.info("Ensuring AVIATION images table exists...");
        aviationRepository.createSchemaIfNotExists();
        log.info("AVIATION images table ready.");
    }
}
