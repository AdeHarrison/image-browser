package uk.co.community.imagebrowser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.model.ImageSummary;
import uk.co.community.imagebrowser.repository.ImageRepository;

import java.util.List;
import java.util.Optional;

/**
 * Handles search queries and image navigation (first/prev/next/last).
 */
@Service
public class ImageSearchService {

    private static final int DEFAULT_PAGE_SIZE = 30;

    private final ImageRepository repository;
    private final int             pageSize;

    public ImageSearchService(
            ImageRepository repository,
            @Value("${app.search.page-size:30}") int pageSize) {
        this.repository = repository;
        this.pageSize   = pageSize;
    }

    public List<ImageSummary> search(String query, int page) {
        int offset = page * pageSize;
        return repository.search(query, offset, pageSize);
    }

    public List<ImageSummary> search(String query) {
        return search(query, 0);
    }

    public Optional<ImageSummary> findById(long id) {
        return repository.findSummaryById(id);
    }

    public NavigationContext getNavigationContext(long id) {
        return new NavigationContext(
                repository.findFirstId().orElse(null),
                repository.findPrevId(id).orElse(null),
                repository.findNextId(id).orElse(null),
                repository.findLastId().orElse(null),
                id
        );
    }

    public long count() {
        return repository.count();
    }

    /**
     * Navigation state for a given image — used to enable/disable nav buttons.
     */
    public record NavigationContext(
            Long  firstId,
            Long  prevId,
            Long  nextId,
            Long  lastId,
            long  currentId
    ) {
        public boolean hasPrev()  { return prevId  != null; }
        public boolean hasNext()  { return nextId  != null; }
        public boolean isFirst()  { return firstId != null && firstId == currentId; }
        public boolean isLast()   { return lastId  != null && lastId  == currentId; }
    }
}
