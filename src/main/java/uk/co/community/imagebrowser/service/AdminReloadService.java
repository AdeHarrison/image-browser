package uk.co.community.imagebrowser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs the admin "reload" (OutputSyncService.sync() then AviationImportService.reimport())
 * on a background thread so AdminController can return immediately and let the UI poll
 * ImportProgressService for a percentage, instead of the browser sitting on one long
 * blocking POST with no feedback until it finishes.
 */
@Service
public class AdminReloadService {

    private static final Logger log = LoggerFactory.getLogger(AdminReloadService.class);

    private final OutputSyncService     outputSyncService;
    private final AviationImportService aviationImportService;
    private final ImportProgressService progressService;

    public AdminReloadService(OutputSyncService     outputSyncService,
                               AviationImportService aviationImportService,
                               ImportProgressService progressService) {
        this.outputSyncService     = outputSyncService;
        this.aviationImportService = aviationImportService;
        this.progressService       = progressService;
    }

    @Async
    public void runReload() {
        try {
            int copied = outputSyncService.sync();
            aviationImportService.reimport();
            progressService.succeed("%d image(s) copied.".formatted(copied));
        } catch (Exception e) {
            log.error("Admin reload failed: {}", e.getMessage(), e);
            progressService.fail(e.getMessage());
        }
    }
}
