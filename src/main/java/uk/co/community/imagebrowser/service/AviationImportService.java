package uk.co.community.imagebrowser.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.model.AviationImageRecord;
import uk.co.community.imagebrowser.repository.AviationImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the AVIATION sheet (columns: REF. NO., LOCATION, DATE, DESCRIPTION/ NOTES,
 * Folder/Page No, Image File Name, ...) and loads it into PostgreSQL via
 * AviationImageRepository. Row 0 is the header and is skipped.
 *
 * DATE and DESCRIPTION are combined into a single searchable description, e.g.
 * "Mr Hucks publicity photograph (1911)" — the "(date)" suffix is omitted when
 * DATE is blank.
 */
@Service
public class AviationImportService {

    private static final Logger log = LoggerFactory.getLogger(AviationImportService.class);

    private static final String SHEET_NAME = "AVIATION";
    private static final String CATEGORY   = "AVIATION";

    private static final int COL_DATE        = 2;
    private static final int COL_DESCRIPTION = 3;
    private static final int COL_FOLDER      = 4;
    private static final int COL_FILE_NAME   = 5;

    private final AviationImageRepository repository;
    private final Path                    spreadsheetPath;

    public AviationImportService(
            AviationImageRepository repository,
            @Value("${app.spreadsheet.path:data/input/Archive_Index_Numbers_Current.xlsx}") String spreadsheetPath) {
        this.repository      = repository;
        this.spreadsheetPath = Path.of(spreadsheetPath);
    }

    /**
     * Drops and recreates the images table, then imports every valid AVIATION
     * row (a row is skipped, not an error, when description/folder/file name
     * is blank — the sheet has trailing empty formatted rows).
     *
     * @return the number of records imported
     */
    public int reimport() throws IOException {
        if (!Files.exists(spreadsheetPath)) {
            log.warn("Spreadsheet not found at: {}", spreadsheetPath);
            return 0;
        }

        try (var fis      = Files.newInputStream(spreadsheetPath);
             var workbook = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                log.warn("'{}' sheet not found in spreadsheet.", SHEET_NAME);
                return 0;
            }

            repository.resetSchema();

            int imported = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String description = readString(row, COL_DESCRIPTION);
                String folder       = readString(row, COL_FOLDER);
                String fileName     = readString(row, COL_FILE_NAME);

                if (description == null || folder == null || fileName == null) {
                    continue;
                }

                String date = readDate(row, COL_DATE);
                String combinedDescription = date != null
                        ? "%s (%s)".formatted(description, date)
                        : description;

                repository.insert(new AviationImageRecord(CATEGORY, date, combinedDescription, folder, fileName));
                imported++;
            }

            log.info("AVIATION import complete: {} record(s) imported.", imported);
            return imported;
        }
    }

    private String readString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatWholeNumber(cell.getNumericCellValue());
            default -> null;
        };

        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private String readDate(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> formatWholeNumber(cell.getNumericCellValue());
            case STRING -> {
                String s = cell.getStringCellValue().trim();
                yield s.isEmpty() ? null : s;
            }
            default -> null;
        };
    }

    private String formatWholeNumber(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
