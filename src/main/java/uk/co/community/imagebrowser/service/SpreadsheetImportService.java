package uk.co.community.imagebrowser.service;

import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.model.AppConfig;
import uk.co.community.imagebrowser.model.ImageRecord;
import uk.co.community.imagebrowser.repository.ImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Reads an Excel (.xlsx) file, extracts embedded images + thumbnails,
 * and persists them to SQLite.
 *
 * Checks a version string, configured via app.spreadsheet.version, before
 * importing — if it matches what's already stored, the import is skipped.
 * There is no longer any in-spreadsheet timestamp cell; the version is a
 * plain config value that must be bumped manually to trigger a reimport.
 */
@Service
public class SpreadsheetImportService {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetImportService.class);

    private final ImageRepository  repository;
    private final ThumbnailService thumbnailService;
    private final ImageCacheService cacheService;
    private final Path             spreadsheetPath;
    private final String           configuredVersion;

    public SpreadsheetImportService(
            ImageRepository  repository,
            ThumbnailService thumbnailService,
            ImageCacheService cacheService,
            @Value("${app.spreadsheet.path:data/input/Archive_Index_Numbers_Current.xlsx}") String spreadsheetPath,
            @Value("${app.spreadsheet.version:}") String spreadsheetVersion) {
        this.repository      = repository;
        this.thumbnailService = thumbnailService;
        this.cacheService    = cacheService;
        this.spreadsheetPath = Path.of(spreadsheetPath);
        this.configuredVersion = (spreadsheetVersion == null || spreadsheetVersion.isBlank())
                ? null : spreadsheetVersion;
    }

    /**
     * Import images from the configured spreadsheet.
     * Skips if the spreadsheet's last-updated value matches what is stored in the DB.
     *
     * @return ImportResult describing what happened
     */
    public ImportResult importIfUpdated() throws IOException {
        if (!Files.exists(spreadsheetPath)) {
            log.warn("Spreadsheet not found at: {}", spreadsheetPath);
            return ImportResult.notFound(spreadsheetPath.toString());
        }

        try (var fis      = Files.newInputStream(spreadsheetPath);
             var workbook = new XSSFWorkbook(fis)) {

            String storedVersion = repository.getConfig(AppConfig.LAST_UPDATED_KEY)
                                              .orElse(null);

            if (storedVersion != null && configuredVersion != null) {
                LocalDateTime dtConfiguredVersion = LocalDateTime.parse(configuredVersion);
                LocalDateTime dtStoredVersion     = LocalDateTime.parse(storedVersion);

                if (!dtConfiguredVersion.isAfter(dtStoredVersion)) {
                    log.info("Spreadsheet unchanged (version={}). Skipping import.", storedVersion);
                    return ImportResult.skipped(configuredVersion);
                }
            }

            log.info("Spreadsheet changed ({} → {}). Starting full import.",
                    storedVersion, configuredVersion);

            repository.clearAll();
            cacheService.invalidateAll();

            int imported = importImages(workbook);

            if (configuredVersion != null) {
                repository.setConfig(AppConfig.LAST_UPDATED_KEY, configuredVersion);
            }

            log.info("Import complete. {} images imported.", imported);
            return ImportResult.success(imported, configuredVersion);
        }
    }

    /**
     * Force a full reimport regardless of version.
     */
    public ImportResult forceImport() throws IOException {
        if (!Files.exists(spreadsheetPath)) {
            return ImportResult.notFound(spreadsheetPath.toString());
        }
        try (var fis      = Files.newInputStream(spreadsheetPath);
             var workbook = new XSSFWorkbook(fis)) {

            repository.clearAll();
            cacheService.invalidateAll();
            int imported = importImages(workbook);
            if (configuredVersion != null) {
                repository.setConfig(AppConfig.LAST_UPDATED_KEY, configuredVersion);
            }
            log.info("Force import complete. {} images imported.", imported);
            return ImportResult.success(imported, configuredVersion);
        }
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private int importImages(XSSFWorkbook workbook) throws IOException {
        int count = 0;
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            XSSFSheet   sheet   = workbook.getSheetAt(s);
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            if (drawing == null) continue;

            for (XSSFShape shape : drawing.getShapes()) {
                if (!(shape instanceof XSSFPicture picture)) continue;

                XSSFPictureData data = picture.getPictureData();
                String          ext  = data.suggestFileExtension();

                if (thumbnailService.isVectorFormat(ext)) {
                    log.debug("Skipping vector image on sheet '{}' ({})", sheet.getSheetName(), ext);
                    continue;
                }

                byte[] fullBytes  = data.getData();
                byte[] thumbBytes = thumbnailService.createThumbnail(fullBytes);

                if (thumbBytes.length == 0) {
                    log.warn("Could not create thumbnail for image on sheet '{}', skipping.",
                            sheet.getSheetName());
                    continue;
                }

                XSSFClientAnchor anchor  = (XSSFClientAnchor) picture.getAnchor();
                String           cellRef = "R%dC%d".formatted(
                                               anchor.getRow1(), anchor.getCol1());

                var record = new ImageRecord(
                        0L,
                        "sheet_%s_img_%d.%s".formatted(sheet.getSheetName(), count, ext),
                        sheet.getSheetName(),
                        cellRef,
                        data.getMimeType(),
                        fullBytes,
                        thumbBytes,
                        0,   // width resolved during thumbnail creation
                        0,   // height resolved during thumbnail creation
                        "",  // tags — enriched later or via admin
                        ""   // description
                );

                repository.insert(record);
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------
    // Result record
    // ---------------------------------------------------------------

    public record ImportResult(
            Status status,
            int    imagesImported,
            String version,
            String message
    ) {
        public enum Status { SUCCESS, SKIPPED, NOT_FOUND }

        static ImportResult success(int count, String version) {
            return new ImportResult(Status.SUCCESS, count, version,
                    count + " images imported successfully.");
        }
        static ImportResult skipped(String version) {
            return new ImportResult(Status.SKIPPED, 0, version,
                    "Spreadsheet unchanged — import skipped.");
        }
        static ImportResult notFound(String path) {
            return new ImportResult(Status.NOT_FOUND, 0, null,
                    "Spreadsheet not found: " + path);
        }
    }
}
