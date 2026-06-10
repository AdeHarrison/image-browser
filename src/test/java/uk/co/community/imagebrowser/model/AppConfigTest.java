package uk.co.community.imagebrowser.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AppConfig")
class AppConfigTest {

    @Test
    @DisplayName("exposes its key and value")
    void appConfig_WhenCreated_ShouldExposeKeyAndValue() {
        var config = new AppConfig("last_updated", "2026-05-22T10:30:00");

        assertThat(config.key()).isEqualTo("last_updated");
        assertThat(config.value()).isEqualTo("2026-05-22T10:30:00");
        assertThat(AppConfig.LAST_UPDATED_KEY).isEqualTo("last_updated");
    }
}
