package uk.co.community.imagebrowser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.*;

@DisplayName("ImageBrowserApplication")
class ImageBrowserApplicationTest {

    @Test
    @DisplayName("main delegates to SpringApplication.run")
    void main_WhenInvoked_ShouldRunSpringApplication() {
        try (var mocked = mockStatic(SpringApplication.class)) {
            String[] args = {"--server.port=0"};

            ImageBrowserApplication.main(args);

            mocked.verify(() -> SpringApplication.run(ImageBrowserApplication.class, args));
        }
    }
}
