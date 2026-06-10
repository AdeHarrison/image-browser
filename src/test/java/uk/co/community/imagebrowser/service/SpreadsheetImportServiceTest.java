package uk.co.community.imagebrowser.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.model.AppConfig;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.service.SpreadsheetImportService.ImportResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpreadsheetImportService")
class SpreadsheetImportServiceTest {

    @Mock private ImageRepository  repository;
    @Mock private ThumbnailService thumbnailService;
    @Mock private ImageCacheService cacheService;

    private SpreadsheetImportService service;

    @BeforeEach
    void setUp() {
        service = new SpreadsheetImportService(
                repository, thumbnailService, cacheService,
                "nonexistent-spreadsheet.xlsx");
    }

    @Test
    @DisplayName("importIfUpdated returns NOT_FOUND when spreadsheet file is missing")
    void importIfUpdated_WhenSpreadsheetMissing_ShouldReturnNotFound() throws IOException {
        var result = service.importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.NOT_FOUND);
        assertThat(result.message()).contains("nonexistent-spreadsheet.xlsx");
    }

    @Test
    @DisplayName("forceImport returns NOT_FOUND when spreadsheet file is missing")
    void forceImport_WhenSpreadsheetMissing_ShouldReturnNotFound() throws IOException {
        var result = service.forceImport();

        assertThat(result.status()).isEqualTo(ImportResult.Status.NOT_FOUND);
    }

    @Test
    @DisplayName("forceImport reimports unconditionally without consulting the stored version")
    void forceImport_WhenSpreadsheetPresent_ShouldReimportIgnoringStoredVersion(@TempDir Path dir) throws IOException {
        Path file = writeWorkbook(dir, "2026-05-22T10:30:00");

        var result = serviceFor(file).forceImport();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.version()).isEqualTo("2026-05-22T10:30:00");
        verify(repository, never()).getConfig(any());
        verify(repository).clearAll();
        verify(cacheService).invalidateAll();
        verify(repository).setConfig(AppConfig.LAST_UPDATED_KEY, "2026-05-22T10:30:00");
    }

    @Test
    @DisplayName("forceImport imports but does not store a version when Metadata is absent")
    void forceImport_WhenVersionAbsent_ShouldImportWithoutSettingConfig(@TempDir Path dir) throws IOException {
        Path file = writeWorkbookWithoutMetadata(dir);

        var result = serviceFor(file).forceImport();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.version()).isNull();
        verify(repository).clearAll();
        verify(cacheService).invalidateAll();
        verify(repository, never()).setConfig(any(), any());
    }

    // ---------------------------------------------------------------
    // importIfUpdated — version-gating (import only if spreadsheet is newer)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importIfUpdated imports when the DB has no stored version yet")
    void importIfUpdated_WhenNoStoredVersion_ShouldImport(@TempDir Path dir) throws IOException {
        Path file = writeWorkbook(dir, "2026-05-22T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY)).thenReturn(Optional.empty());

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        verify(repository).clearAll();
        verify(cacheService).invalidateAll();
        verify(repository).setConfig(AppConfig.LAST_UPDATED_KEY, "2026-05-22T10:30:00");
    }

    @Test
    @DisplayName("importIfUpdated imports when the spreadsheet date is after the stored date")
    void importIfUpdated_WhenSpreadsheetNewerThanStored_ShouldImport(@TempDir Path dir) throws IOException {
        Path file = writeWorkbook(dir, "2026-05-22T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY))
                .thenReturn(Optional.of("2026-05-21T10:30:00"));

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        verify(repository).clearAll();
        verify(cacheService).invalidateAll();
        verify(repository).setConfig(AppConfig.LAST_UPDATED_KEY, "2026-05-22T10:30:00");
    }

    @Test
    @DisplayName("importIfUpdated skips when the spreadsheet date equals the stored date")
    void importIfUpdated_WhenSpreadsheetEqualsStored_ShouldSkip(@TempDir Path dir) throws IOException {
        Path file = writeWorkbook(dir, "2026-05-22T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY))
                .thenReturn(Optional.of("2026-05-22T10:30:00"));

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SKIPPED);
        verify(repository, never()).clearAll();
        verify(repository, never()).setConfig(any(), any());
        verify(cacheService, never()).invalidateAll();
    }

    @Test
    @DisplayName("importIfUpdated imports but stores no version when the Metadata sheet is absent")
    void importIfUpdated_WhenNoStoredVersionAndNoMetadata_ShouldImportWithoutSettingConfig(@TempDir Path dir) throws IOException {
        Path file = writeWorkbookWithoutMetadata(dir);
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY)).thenReturn(Optional.empty());

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.version()).isNull();
        verify(repository).clearAll();
        verify(repository, never()).setConfig(any(), any());
    }

    @Test
    @DisplayName("importIfUpdated extracts and inserts an embedded picture")
    void importIfUpdated_WhenWorkbookHasPicture_ShouldImportImage(@TempDir Path dir) throws IOException {
        Path file = writeWorkbookWithPicture(dir, "2026-05-22T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY)).thenReturn(Optional.empty());
        when(thumbnailService.isVectorFormat(anyString())).thenReturn(false);
        when(thumbnailService.createThumbnail(any())).thenReturn(new byte[]{9});

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.imagesImported()).isEqualTo(1);
        verify(repository).insert(any());
    }

    @Test
    @DisplayName("importIfUpdated skips vector images")
    void importIfUpdated_WhenPictureIsVector_ShouldSkipImage(@TempDir Path dir) throws IOException {
        Path file = writeWorkbookWithPicture(dir, "2026-05-22T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY)).thenReturn(Optional.empty());
        when(thumbnailService.isVectorFormat(anyString())).thenReturn(true);

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.imagesImported()).isZero();
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("importIfUpdated skips images whose thumbnail cannot be created")
    void importIfUpdated_WhenThumbnailEmpty_ShouldSkipImage(@TempDir Path dir) throws IOException {
        Path file = writeWorkbookWithPicture(dir, "2026-05-22T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY)).thenReturn(Optional.empty());
        when(thumbnailService.isVectorFormat(anyString())).thenReturn(false);
        when(thumbnailService.createThumbnail(any())).thenReturn(new byte[0]);

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.imagesImported()).isZero();
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("importIfUpdated skips when the spreadsheet date is before the stored date")
    void importIfUpdated_WhenSpreadsheetOlderThanStored_ShouldSkip(@TempDir Path dir) throws IOException {
        Path file = writeWorkbook(dir, "2026-05-20T10:30:00");
        when(repository.getConfig(AppConfig.LAST_UPDATED_KEY))
                .thenReturn(Optional.of("2026-05-22T10:30:00"));

        var result = serviceFor(file).importIfUpdated();

        assertThat(result.status()).isEqualTo(ImportResult.Status.SKIPPED);
        verify(repository, never()).clearAll();
        verify(repository, never()).setConfig(any(), any());
        verify(cacheService, never()).invalidateAll();
    }

    // ---------------------------------------------------------------
    // readLastUpdated
    // ---------------------------------------------------------------

    @Test
    @DisplayName("readLastUpdated returns null when Metadata sheet is absent")
    void readLastUpdated_WhenMetadataSheetAbsent_ShouldReturnNull() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("Data");
            assertThat(service.readLastUpdated(wb)).isNull();
        }
    }

    @Test
    @DisplayName("readLastUpdated returns null when Metadata sheet has no rows")
    void readLastUpdated_WhenMetadataSheetEmpty_ShouldReturnNull() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("Metadata");
            assertThat(service.readLastUpdated(wb)).isNull();
        }
    }

    @Test
    @DisplayName("readLastUpdated reads B1, ignoring a value in A1")
    void readLastUpdated_WhenA1AndB1Differ_ShouldReturnB1() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var row = wb.createSheet("Metadata").createRow(0);
            row.createCell(0).setCellValue("A1-should-be-ignored");
            row.createCell(1).setCellValue("2026-05-22T10:30:00");

            assertThat(service.readLastUpdated(wb)).isEqualTo("2026-05-22T10:30:00");
        }
    }

    @Test
    @DisplayName("readLastUpdated returns null when only A1 is populated (B1 empty)")
    void readLastUpdated_WhenOnlyA1Present_ShouldReturnNull() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var row = wb.createSheet("Metadata").createRow(0);
            row.createCell(0).setCellValue("2026-05-22T10:30:00");

            assertThat(service.readLastUpdated(wb)).isNull();
        }
    }

    @Test
    @DisplayName("readLastUpdated returns null when B1 is not a string cell")
    void readLastUpdated_WhenB1NotString_ShouldReturnNull() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var row = wb.createSheet("Metadata").createRow(0);
            row.createCell(1).setCellValue(42.0);   // numeric — getStringCellValue throws

            assertThat(service.readLastUpdated(wb)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // ImportResult
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ImportResult.success has correct status and message")
    void importResultSuccess_WhenCreated_ShouldHaveSuccessStatusAndMessage() {
        var result = ImportResult.success(42, "2026-05-22");
        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCESS);
        assertThat(result.imagesImported()).isEqualTo(42);
        assertThat(result.message()).contains("42");
    }

    @Test
    @DisplayName("ImportResult.skipped has correct status")
    void importResultSkipped_WhenCreated_ShouldHaveSkippedStatus() {
        var result = ImportResult.skipped("2026-05-22");
        assertThat(result.status()).isEqualTo(ImportResult.Status.SKIPPED);
        assertThat(result.imagesImported()).isZero();
    }

    @Test
    @DisplayName("ImportResult.notFound has correct status")
    void importResultNotFound_WhenCreated_ShouldHaveNotFoundStatus() {
        var result = ImportResult.notFound("/path/to/file.xlsx");
        assertThat(result.status()).isEqualTo(ImportResult.Status.NOT_FOUND);
        assertThat(result.message()).contains("/path/to/file.xlsx");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private SpreadsheetImportService serviceFor(Path spreadsheet) {
        return new SpreadsheetImportService(
                repository, thumbnailService, cacheService, spreadsheet.toString());
    }

    /** Writes a minimal .xlsx with the given last-updated timestamp in Metadata!B1. */
    private Path writeWorkbook(Path dir, String timestamp) throws IOException {
        Path file = dir.resolve("spreadsheet.xlsx");
        try (var wb = new XSSFWorkbook()) {
            var row = wb.createSheet("Metadata").createRow(0);
            row.createCell(1).setCellValue(timestamp);
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    /** Writes a minimal .xlsx with no Metadata sheet, so the last-updated version is null. */
    private Path writeWorkbookWithoutMetadata(Path dir) throws IOException {
        Path file = dir.resolve("spreadsheet.xlsx");
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("Data");
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    /** Writes an .xlsx with a Metadata timestamp plus a sheet holding one embedded PNG. */
    private Path writeWorkbookWithPicture(Path dir, String timestamp) throws IOException {
        Path file = dir.resolve("spreadsheet.xlsx");
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("Metadata").createRow(0).createCell(1).setCellValue(timestamp);

            var sheet   = wb.createSheet("Images");
            int picIdx  = wb.addPicture(tinyPng(), Workbook.PICTURE_TYPE_PNG);
            var drawing = sheet.createDrawingPatriarch();
            var anchor  = new XSSFClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(0);
            drawing.createPicture(anchor, picIdx);

            // A non-picture shape, exercising the "skip non-picture" path in importImages.
            var textAnchor = new XSSFClientAnchor();
            textAnchor.setCol1(3);
            textAnchor.setRow1(3);
            textAnchor.setCol2(5);
            textAnchor.setRow2(5);
            drawing.createTextbox(textAnchor);

            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    private byte[] tinyPng() throws IOException {
        var img  = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
