package uk.co.community.imagebrowser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.service.SpreadsheetImportService;
import uk.co.community.imagebrowser.service.SpreadsheetImportService.ImportResult;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseInitialiser")
class DatabaseInitialiserTest {

    @Mock private ImageRepository          repository;
    @Mock private SpreadsheetImportService importService;

    @Test
    @DisplayName("initialise creates the schema then runs a version-gated import")
    void initialise_WhenInvoked_ShouldCreateSchemaAndImport() throws IOException {
        when(importService.importIfUpdated())
                .thenReturn(new ImportResult(ImportResult.Status.SKIPPED, 0, "v1", "skipped"));

        new DatabaseInitialiser(repository, importService).initialise();

        verify(repository).createSchema();
        verify(importService).importIfUpdated();
    }

    @Test
    @DisplayName("initialise swallows import failures so application startup continues")
    void initialise_WhenImportThrows_ShouldNotPropagate() throws IOException {
        when(importService.importIfUpdated()).thenThrow(new IOException("boom"));

        new DatabaseInitialiser(repository, importService).initialise();

        verify(repository).createSchema();
        verify(importService).importIfUpdated();
    }
}
