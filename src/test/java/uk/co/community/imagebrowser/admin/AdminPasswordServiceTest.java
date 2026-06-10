package uk.co.community.imagebrowser.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AdminPasswordService")
class AdminPasswordServiceTest {

    @Test
    @DisplayName("verify returns false before load() has run (no password set)")
    void verify_WhenNotLoaded_ShouldReturnFalse() {
        var svc = new AdminPasswordService("whatever.properties");

        assertThat(svc.verify("anything")).isFalse();
    }

    @Test
    @DisplayName("load falls back to the default password when the file is missing")
    void load_WhenFileMissing_ShouldUseDefaultPassword(@TempDir Path dir) {
        var svc = new AdminPasswordService(dir.resolve("absent.properties").toString());

        svc.load();

        assertThat(svc.verify("changeme")).isTrue();
        assertThat(svc.verify("nope")).isFalse();
    }

    @Test
    @DisplayName("load reads the password from the properties file")
    void load_WhenFilePresent_ShouldReadConfiguredPassword(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("admin.properties");
        Files.writeString(file, "admin.password=s3cret\n");
        var svc = new AdminPasswordService(file.toString());

        svc.load();

        assertThat(svc.verify("s3cret")).isTrue();
        assertThat(svc.verify("wrong")).isFalse();
    }

    @Test
    @DisplayName("load falls back to the default when the key is absent from the file")
    void load_WhenKeyAbsent_ShouldUseDefaultPassword(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("admin.properties");
        Files.writeString(file, "other.key=value\n");
        var svc = new AdminPasswordService(file.toString());

        svc.load();

        assertThat(svc.verify("changeme")).isTrue();
    }

    @Test
    @DisplayName("load falls back to the default when the path cannot be read as a file")
    void load_WhenPathUnreadable_ShouldUseDefaultPassword(@TempDir Path dir) {
        // The path exists but is a directory — opening it as an input stream throws IOException.
        var svc = new AdminPasswordService(dir.toString());

        svc.load();

        assertThat(svc.verify("changeme")).isTrue();
    }
}
