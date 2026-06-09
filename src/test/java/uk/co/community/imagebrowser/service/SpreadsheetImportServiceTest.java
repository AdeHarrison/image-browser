package uk.co.community.imagebrowser.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.service.SpreadsheetImportService.ImportResult;

import java.io.IOException;
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
    @DisplayName("readLastUpdated reads value from Metadata!A1")
    void readLastUpdated_WhenMetadataA1Present_ShouldReturnValue() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Metadata");
            var row   = sheet.createRow(0);
            row.createCell(0).setCellValue("2026-05-22T10:30:00");

            assertThat(service.readLastUpdated(wb)).isEqualTo("2026-05-22T10:30:00");
        }
    }

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
}
