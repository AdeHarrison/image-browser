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
 */
@Component
public class SpreadsheetAvailabilityCheck {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetAvailabilityCheck.class);

    private static final String AVIATION_SHEET_NAME = "AVIATION";

    private final Path spreadsheetPath;

    public SpreadsheetAvailabilityCheck(
            @Value("${app.spreadsheet.path:spreadsheet.xlsx}") String spreadsheetPath) {
        this.spreadsheetPath = Path.of(spreadsheetPath);
    }

    @PostConstruct
    void checkAvailability() {
        if (!checkFileExists()) {
            return;
        }
        checkAviationSheetExists();
    }

    private boolean checkFileExists() {
        Path resolved = spreadsheetPath.toAbsolutePath().normalize();

        if (!Files.exists(spreadsheetPath)) {
            log.warn("Spreadsheet NOT found at app.spreadsheet.path: {}", resolved);
            return false;
        }

        try {
            long sizeBytes = Files.size(spreadsheetPath);
            log.info("Spreadsheet found at app.spreadsheet.path: {} ({} bytes)", resolved, sizeBytes);
            return true;
        } catch (IOException e) {
            log.warn("Spreadsheet found at {} but could not be read: {}", resolved, e.getMessage());
            return false;
        }
    }

    private void checkAviationSheetExists() {
        try (var fis = Files.newInputStream(spreadsheetPath);
             var workbook = new XSSFWorkbook(fis)) {

            if (workbook.getSheet(AVIATION_SHEET_NAME) != null) {
                log.info("'{}' sheet found in spreadsheet.", AVIATION_SHEET_NAME);
            } else {
                log.warn("'{}' sheet NOT found in spreadsheet.", AVIATION_SHEET_NAME);
            }
        } catch (IOException e) {
            log.warn("Could not open spreadsheet to check for '{}' sheet: {}",
                    AVIATION_SHEET_NAME, e.getMessage());
        }
    }
}
