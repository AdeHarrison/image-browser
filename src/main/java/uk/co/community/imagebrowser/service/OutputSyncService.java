package uk.co.community.imagebrowser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Clears the output directory and repopulates it from the input directory:
 *   - top-level files (the spreadsheet itself) are copied as-is
 *   - files nested under sheet/folder subdirectories (the images) are copied
 *     with a "FULL-" filename prefix, and a thumbnail of each is written
 *     alongside it with a "THUMB-" filename prefix
 *
 * No DB writes — filesystem sync only. This is a first step towards the new
 * file-based importer. Only runs when triggered from the admin "reload"
 * action (AdminController) — NOT automatically on startup.
 */
@Service
public class OutputSyncService {

    private static final Logger log = LoggerFactory.getLogger(OutputSyncService.class);

    private static final String FULL_PREFIX  = "FULL-";
    private static final String THUMB_PREFIX = "THUMB-";

    private final Path             inputDir;
    private final Path             outputDir;
    private final ThumbnailService thumbnailService;

    public OutputSyncService(
            @Value("${app.spreadsheet.path:data/input/Archive_Index_Numbers_Current.xlsx}") String spreadsheetPath,
            @Value("${app.data.output-dir:data/output}") String outputDir,
            ThumbnailService thumbnailService) {
        this.inputDir  = Path.of(spreadsheetPath).toAbsolutePath().normalize().getParent();
        this.outputDir = Path.of(outputDir);
        this.thumbnailService = thumbnailService;
    }

    /**
     * Wipes everything below the output directory, then copies the input
     * directory tree into it.
     *
     * @return the number of files copied
     */
    public int sync() throws IOException {
        clearOutputDir();
        int copied = copyTree();
        log.info("Output sync complete: {} file(s) copied from {} to {}", copied, inputDir, outputDir);
        return copied;
    }

    private void clearOutputDir() throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            return;
        }
        try (var walk = Files.walk(outputDir)) {
            walk.filter(path -> !path.equals(outputDir))
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }

    private int copyTree() throws IOException {
        if (!Files.isDirectory(inputDir)) {
            log.warn("Input directory not found: {}", inputDir);
            return 0;
        }

        int count = 0;
        try (var walk = Files.walk(inputDir)) {
            var files = walk.filter(Files::isRegularFile).toList();
            for (Path source : files) {
                Path   relative   = inputDir.relativize(source);
                boolean topLevel  = relative.getNameCount() == 1;
                Path targetDirForFile = topLevel
                        ? outputDir
                        : outputDir.resolve(relative.getParent());

                Files.createDirectories(targetDirForFile);

                if (topLevel) {
                    Path targetFile = targetDirForFile.resolve(source.getFileName().toString());
                    Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    String fileName   = source.getFileName().toString();
                    Path   targetFile = targetDirForFile.resolve(FULL_PREFIX + fileName);
                    Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    writeThumbnail(source, fileName, targetDirForFile);
                }
                count++;
            }
        }
        return count;
    }

    private void writeThumbnail(Path source, String fileName, Path targetDir) {
        String ext = extensionOf(fileName);
        if (thumbnailService.isVectorFormat(ext)) {
            log.debug("Skipping thumbnail for vector image {}", source);
            return;
        }

        try {
            byte[] fullBytes  = Files.readAllBytes(source);
            byte[] thumbBytes = thumbnailService.createThumbnail(fullBytes);
            if (thumbBytes.length == 0) {
                log.warn("Could not create thumbnail for {}, skipping.", source);
                return;
            }
            Files.write(targetDir.resolve(THUMB_PREFIX + fileName), thumbBytes);
        } catch (IOException e) {
            log.warn("Could not create thumbnail for {}: {}", source, e.getMessage());
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }
}
