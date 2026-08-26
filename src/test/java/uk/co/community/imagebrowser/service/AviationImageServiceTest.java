package uk.co.community.imagebrowser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.model.AviationImageSummary;
import uk.co.community.imagebrowser.repository.AviationImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AviationImageService")
class AviationImageServiceTest {

    private static final long ID = 1L;

    @Mock private AviationImageRepository repository;

    @TempDir private Path root;

    private Path outputDir;
    private AviationImageService service;

    @BeforeEach
    void setUp() throws IOException {
        outputDir = root.resolve("data").resolve("output");
        Files.createDirectories(outputDir);
        service = new AviationImageService(repository, outputDir.toString(), 60);
    }

    @Test
    @DisplayName("getThumbnail reads THUMB-prefixed bytes from category/folder under the output dir")
    void getThumbnail_WithValidSummary_ShouldReturnFileBytes() throws IOException {
        Path dir = outputDir.resolve("AVIATION").resolve("01");
        Files.createDirectories(dir);
        byte[] bytes = "thumb-bytes".getBytes();
        Files.write(dir.resolve("THUMB-01-01.jpg"), bytes);

        AviationImageSummary summary = new AviationImageSummary(ID, "AVIATION", "01", "01-01.jpg", "1911", "desc");
        when(repository.findById(ID)).thenReturn(Optional.of(summary));

        assertThat(service.getThumbnail(ID)).contains(bytes);
    }

    @Test
    @DisplayName("getFullImage rejects a folder value that resolves outside the output dir")
    void getFullImage_WithPathTraversalInFolder_ShouldReturnEmpty() throws IOException {
        // "AVIATION" + "../../.." cancels back out to root — a file that genuinely exists
        // there, so the only thing preventing it being served is the containment check,
        // not a missing file.
        Path secret = root.resolve("FULL-secret.jpg");
        Files.write(secret, "leaked".getBytes());

        AviationImageSummary summary =
                new AviationImageSummary(ID, "AVIATION", "../../..", "secret.jpg", "1911", "desc");
        when(repository.findById(ID)).thenReturn(Optional.of(summary));

        assertThat(service.getFullImage(ID)).isEmpty();
    }

    @Test
    @DisplayName("getThumbnail rejects a category value that resolves outside the output dir")
    void getThumbnail_WithPathTraversalInCategory_ShouldReturnEmpty() throws IOException {
        // "../.." cancels output/data back out to root, then "01"/"THUMB-secret.jpg" — a
        // file that genuinely exists there, so only the containment check stops it.
        Path secretDir = root.resolve("01");
        Files.createDirectories(secretDir);
        Files.write(secretDir.resolve("THUMB-secret.jpg"), "leaked".getBytes());

        AviationImageSummary summary =
                new AviationImageSummary(ID, "../..", "01", "secret.jpg", "1911", "desc");
        when(repository.findById(ID)).thenReturn(Optional.of(summary));

        assertThat(service.getThumbnail(ID)).isEmpty();
    }

    @Test
    @DisplayName("getThumbnail returns empty when the file does not exist on disk")
    void getThumbnail_WhenFileMissing_ShouldReturnEmpty() {
        AviationImageSummary summary = new AviationImageSummary(ID, "AVIATION", "01", "missing.jpg", "1911", "desc");
        when(repository.findById(ID)).thenReturn(Optional.of(summary));

        assertThat(service.getThumbnail(ID)).isEmpty();
    }
}
