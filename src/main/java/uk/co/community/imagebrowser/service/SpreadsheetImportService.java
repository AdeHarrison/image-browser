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

/**
 * Reads an Excel (.xlsx) file, extracts embedded images + thumbnails,
 * and persists them to SQLite.
 *
 * Checks a "last_updated" cell on the Metadata sheet before importing —
 * if the spreadsheet is unchanged since last import, the import is skipped.
 *
 * Metadata sheet layout (Sheet named "Metadata"):
 *   A1 — last updated timestamp string e.g. "2026-05-22T10:30:00"
 */
@Service
public class SpreadsheetImportService {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetImportService.class);

    private final ImageRepository  repository;
    private final ThumbnailService thumbnailService;
    private final ImageCacheService cacheService;
    private final Path             spreadsheetPath;

    public SpreadsheetImportService(
            ImageRepository  repository,
            ThumbnailService thumbnailService,
            ImageCacheService cacheService,
            @Value("${app.spreadsheet.path:spreadsheet.xlsx}") String spreadsheetPath) {
        this.repository      = repository;
        this.thumbnailService = thumbnailService;
        this.cacheService    = cacheService;
        this.spreadsheetPath = Path.of(spreadsheetPath);
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

            String spreadsheetVersion = readLastUpdated(workbook);
            String storedVersion      = repository.getConfig(AppConfig.LAST_UPDATED_KEY)
                                                  .orElse(null);

            if (spreadsheetVersion != null && spreadsheetVersion.equals(storedVersion)) {
                log.info("Spreadsheet unchanged (version={}). Skipping import.", storedVersion);
                return ImportResult.skipped(spreadsheetVersion);
            }

            log.info("Spreadsheet changed ({} → {}). Starting full import.",
                    storedVersion, spreadsheetVersion);

            repository.clearAll();
            cacheService.invalidateAll();

            int imported = importImages(workbook);

            if (spreadsheetVersion != null) {
                repository.setConfig(AppConfig.LAST_UPDATED_KEY, spreadsheetVersion);
            }

            log.info("Import complete. {} images imported.", imported);
            return ImportResult.success(imported, spreadsheetVersion);
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

            String version = readLastUpdated(workbook);
            repository.clearAll();
            cacheService.invalidateAll();
            int imported = importImages(workbook);
            if (version != null) {
                repository.setConfig(AppConfig.LAST_UPDATED_KEY, version);
            }
            log.info("Force import complete. {} images imported.", imported);
            return ImportResult.success(imported, version);
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

    String readLastUpdated(XSSFWorkbook workbook) {
        XSSFSheet meta = workbook.getSheet("Metadata");
        if (meta == null) return null;
        var row = meta.getRow(0);
        if (row == null) return null;
        var cell = row.getCell(0);
        if (cell == null) return null;
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            return null;
        }
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
