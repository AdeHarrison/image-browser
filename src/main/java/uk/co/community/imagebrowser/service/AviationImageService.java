package uk.co.community.imagebrowser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.model.AviationImageSummary;
import uk.co.community.imagebrowser.repository.AviationImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Read side of the AVIATION image data: description search plus reading
 * thumbnail files back off disk. Source images are physically named
 * "{folder}-{fileName}" (e.g. data/input/AVIATION/4/4-3.jpg for folder=4,
 * fileName=3.jpg) — OutputSyncService copies them verbatim under that name
 * with a THUMB-/FULL- prefix, so the DB's fileName column alone isn't the
 * real filename on disk; the folder prefix has to be reattached here.
 */
@Service
public class AviationImageService {

    private static final Logger log = LoggerFactory.getLogger(AviationImageService.class);

    private static final String THUMB_PREFIX = "THUMB-";
    private static final String FULL_PREFIX  = "FULL-";

    private final AviationImageRepository repository;
    private final Path                    outputDir;
    private final int                     browsePageSize;

    public AviationImageService(
            AviationImageRepository repository,
            @Value("${app.data.output-dir:data/output}") String outputDir,
            @Value("${app.browse.page-size:60}") int browsePageSize) {
        this.repository    = repository;
        this.outputDir     = Path.of(outputDir);
        this.browsePageSize = browsePageSize;
    }

    public List<AviationImageSummary> search(String query) {
        return repository.search(query);
    }

    public long count() {
        return repository.count();
    }

    public int browsePageSize() {
        return browsePageSize;
    }

    /** One page (0-indexed) of every image, ordered by id, for the "browse all" (*) view. */
    public List<AviationImageSummary> browse(int page, int pageSize) {
        return repository.findPage(page * pageSize, pageSize);
    }

    public Optional<AviationImageSummary> findById(long id) {
        return repository.findById(id);
    }

    public Optional<byte[]> getThumbnail(long id) {
        return repository.findById(id).flatMap(summary -> readImageFile(summary, THUMB_PREFIX));
    }

    public Optional<byte[]> getFullImage(long id) {
        return repository.findById(id).flatMap(summary -> readImageFile(summary, FULL_PREFIX));
    }

    private Optional<byte[]> readImageFile(AviationImageSummary summary, String prefix) {
        String diskFileName = summary.folder() + "-" + summary.fileName();
        Path path = outputDir.resolve(summary.category())
                              .resolve(summary.folder())
                              .resolve(prefix + diskFileName);
        try {
            return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException e) {
            log.warn("Could not read image at {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
