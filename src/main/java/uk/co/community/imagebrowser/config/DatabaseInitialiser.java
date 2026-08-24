package uk.co.community.imagebrowser.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.service.SpreadsheetImportService;

/**
 * Runs once on startup:
 *   1. Creates the SQLite schema (idempotent — IF NOT EXISTS)
 *   2. Checks the spreadsheet last-updated value and imports if changed
 */
@Component
public class DatabaseInitialiser {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitialiser.class);

    private final ImageRepository        repository;
    private final SpreadsheetImportService importService;

    public DatabaseInitialiser(ImageRepository repository,
                                SpreadsheetImportService importService) {
        this.repository    = repository;
        this.importService = importService;
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
    }
}
