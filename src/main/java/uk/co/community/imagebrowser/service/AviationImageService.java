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

    private final AviationImageRepository repository;
    private final Path                    outputDir;

    public AviationImageService(
            AviationImageRepository repository,
            @Value("${app.data.output-dir:data/output}") String outputDir) {
        this.repository = repository;
        this.outputDir  = Path.of(outputDir);
    }

    public List<AviationImageSummary> search(String query) {
        return repository.search(query);
    }

    public long count() {
        return repository.count();
    }

    public Optional<byte[]> getThumbnail(long id) {
        return repository.findById(id).flatMap(this::readThumbnail);
    }

    private Optional<byte[]> readThumbnail(AviationImageSummary summary) {
        String diskFileName = summary.folder() + "-" + summary.fileName();
        Path path = outputDir.resolve(summary.category())
                              .resolve(summary.folder())
                              .resolve(THUMB_PREFIX + diskFileName);
        try {
            return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException e) {
            log.warn("Could not read thumbnail at {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
