package uk.co.community.imagebrowser.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.model.AviationImageRecord;
import uk.co.community.imagebrowser.repository.AviationImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AviationImportService")
class AviationImportServiceTest {

    @Mock private AviationImageRepository repository;

    @Test
    @DisplayName("reimport returns 0 and does not touch the repository when the spreadsheet is missing")
    void reimport_WhenSpreadsheetMissing_ShouldReturnZero() throws IOException {
        var service = new AviationImportService(repository, "nonexistent-spreadsheet.xlsx");

        int imported = service.reimport();

        assertThat(imported).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("reimport returns 0 and does not touch the repository when the AVIATION sheet is absent")
    void reimport_WhenAviationSheetAbsent_ShouldReturnZero(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ss.xlsx");
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("Other");
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }

        int imported = new AviationImportService(repository, file.toString()).reimport();

        assertThat(imported).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("reimport resets the schema once, combines date+description, and skips blank trailing rows")
    void reimport_WithMixedRows_ShouldImportOnlyValidRowsWithCombinedDescription(@TempDir Path dir) throws IOException {
        Path file = writeAviationWorkbook(dir);

        int imported = new AviationImportService(repository, file.toString()).reimport();

        assertThat(imported).isEqualTo(2);
        verify(repository).resetSchema();

        var captor = ArgumentCaptor.forClass(AviationImageRecord.class);
        verify(repository, times(2)).insert(captor.capture());

        List<AviationImageRecord> inserted = captor.getAllValues();
        assertThat(inserted.get(0)).isEqualTo(
                new AviationImageRecord("AVIATION", "1911", "Mr Hucks publicity photograph (1911)", "4", "3.jpg"));
        assertThat(inserted.get(1)).isEqualTo(
                new AviationImageRecord("AVIATION", null, "Vickers Vimy bomber forced landing on beach", "7", "1.jpg"));
    }

    /**
     * Header row (skipped), a normal dated row, a row with no date (parens must be
     * omitted), and a trailing formatted-but-empty row (must be skipped, not error) —
     * mirrors the real AVIATION sheet's blank tail rows.
     */
    private Path writeAviationWorkbook(Path dir) throws IOException {
        Path file = dir.resolve("ss.xlsx");
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("AVIATION");

            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("REF. NO.");
            header.createCell(1).setCellValue("LOCATION");
            header.createCell(2).setCellValue("DATE");
            header.createCell(3).setCellValue("DESCRIPTION/ NOTES");
            header.createCell(4).setCellValue("Folder/Page No");
            header.createCell(5).setCellValue("Image File Name");

            var row1 = sheet.createRow(1);
            row1.createCell(1).setCellValue("Burnham");
            row1.createCell(2).setCellValue(1911);
            row1.createCell(3).setCellValue("Mr Hucks publicity photograph");
            row1.createCell(4).setCellValue("4");
            row1.createCell(5).setCellValue("3.jpg");

            var row2 = sheet.createRow(2);
            row2.createCell(1).setCellValue("Burnham");
            row2.createCell(3).setCellValue("Vickers Vimy bomber forced landing on beach");
            row2.createCell(4).setCellValue("7");
            row2.createCell(5).setCellValue("1.jpg");

            var row3 = sheet.createRow(3);
            row3.createCell(2);

            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }
}
