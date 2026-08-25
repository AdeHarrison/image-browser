package uk.co.community.imagebrowser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.repository.AviationImageRepository;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseInitialiser")
class DatabaseInitialiserTest {

    @Mock private AviationImageRepository aviationRepository;

    @Test
    @DisplayName("initialise creates the AVIATION images table if it doesn't exist")
    void initialise_WhenInvoked_ShouldCreateAviationSchemaIfNotExists() {
        new DatabaseInitialiser(aviationRepository).initialise();

        verify(aviationRepository).createSchemaIfNotExists();
    }
}
