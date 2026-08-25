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

    private static final int  DELETE_MAX_ATTEMPTS    = 5;
    private static final long DELETE_RETRY_DELAY_MS  = 100;

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
                .forEach(this::deleteWithRetry);
        }
    }

    /**
     * Deletes a path, retrying on IOException before giving up. On Windows,
     * cloud-sync clients (e.g. Google Drive Desktop, OneDrive) or antivirus
     * scanners can briefly hold an open handle on, or drop their own transient
     * marker files into, a directory that was just written — causing a
     * transient AccessDeniedException on delete that doesn't happen on Linux
     * (no such client). A short retry lets the lock/race clear.
     *
     * If a directory still can't be removed after retries — e.g. the sync
     * client keeps re-populating it faster than we can retry — it's logged
     * and left in place rather than failing the whole reload: copyTree()
     * recreates directories as needed and overwrites files, so a stray
     * leftover directory is harmless and will get cleaned up on a later
     * reload once the sync settles. Files still fail hard, since a file we
     * can't remove may be stale content the new import no longer produces.
     */
    private void deleteWithRetry(Path path) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= DELETE_MAX_ATTEMPTS; attempt++) {
            try {
                Files.delete(path);
                return;
            } catch (IOException e) {
                lastFailure = e;
                if (attempt < DELETE_MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(DELETE_RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        if (Files.isDirectory(path)) {
            log.warn("Could not remove directory {} after {} attempts, leaving it in place: {}",
                    path, DELETE_MAX_ATTEMPTS, lastFailure.getMessage());
            return;
        }
        throw new UncheckedIOException(lastFailure);
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
