package uk.co.community.imagebrowser.config;

import jakarta.annotation.PostConstruct;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Confirms on startup that the spreadsheet configured via app.spreadsheet.path
 * actually exists at that location, and that it contains an "AVIATION" sheet.
 * Any failed check throws, which fails application startup — this is a hard
 * requirement, not an advisory warning.
 */
@Component
public class SpreadsheetAvailabilityCheck {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetAvailabilityCheck.class);

    private static final String AVIATION_SHEET_NAME = "AVIATION";

    private final Path spreadsheetPath;

    public SpreadsheetAvailabilityCheck(
            @Value("${app.spreadsheet.path:data/input/Archive_Index_Numbers_Current.xlsx}") String spreadsheetPath) {
        this.spreadsheetPath = Path.of(spreadsheetPath);
    }

    @PostConstruct
    void checkAvailability() {
        checkFileExists();
        checkAviationSheetExists();
    }

    private void checkFileExists() {
        Path resolved = spreadsheetPath.toAbsolutePath().normalize();

        if (!Files.exists(spreadsheetPath)) {
            throw new IllegalStateException(
                    "Spreadsheet NOT found at app.spreadsheet.path: " + resolved);
        }

        try {
            long sizeBytes = Files.size(spreadsheetPath);
            log.info("Spreadsheet found at app.spreadsheet.path: {} ({} bytes)", resolved, sizeBytes);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Spreadsheet found at %s but could not be read: %s".formatted(resolved, e.getMessage()), e);
        }
    }

    private void checkAviationSheetExists() {
        try (var fis = Files.newInputStream(spreadsheetPath);
             var workbook = new XSSFWorkbook(fis)) {

            if (workbook.getSheet(AVIATION_SHEET_NAME) == null) {
                throw new IllegalStateException(
                        "'%s' sheet NOT found in spreadsheet: %s".formatted(AVIATION_SHEET_NAME, spreadsheetPath));
            }
            log.info("'{}' sheet found in spreadsheet.", AVIATION_SHEET_NAME);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not open spreadsheet to check for '%s' sheet: %s"
                            .formatted(AVIATION_SHEET_NAME, e.getMessage()), e);
        }
    }
}
