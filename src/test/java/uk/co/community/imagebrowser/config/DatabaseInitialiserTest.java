package uk.co.community.imagebrowser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.service.SpreadsheetImportService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseInitialiser")
class DatabaseInitialiserTest {

    @Mock private ImageRepository          repository;
    @Mock private SpreadsheetImportService importService;

    @Test
    @DisplayName("initialise is currently a no-op — DB-backed import is disabled for now")
    void initialise_WhenInvoked_ShouldNotTouchRepositoryOrImportService() {
        new DatabaseInitialiser(repository, importService).initialise();

        verifyNoInteractions(repository, importService);
    }
}
