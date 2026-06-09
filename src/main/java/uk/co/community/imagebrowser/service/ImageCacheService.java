package uk.co.community.imagebrowser.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.repository.ImageRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-tier image cache:
 *   L1 — Caffeine in-memory (weight-bounded + TTL + softValues)
 *   L2 — SQLite via ImageRepository
 *
 * Thumbnails and full images are cached separately with different size budgets.
 */
@Service
public class ImageCacheService {

    private static final Logger log = LoggerFactory.getLogger(ImageCacheService.class);

    private final ImageRepository repository;
    private final boolean         preloadEnabled;
    private final int             preloadPageSize;

    private final Cache<Long, byte[]> thumbnailCache;
    private final Cache<Long, byte[]> fullImageCache;

    public ImageCacheService(
            ImageRepository repository,
            @Value("${app.cache.thumbnail-max-bytes:15728640}") long thumbnailMaxBytes,
            @Value("${app.cache.fullimage-max-bytes:41943040}")  long fullImageMaxBytes,
            @Value("${app.cache.expire-minutes:15}")             long expireMinutes,
            @Value("${app.preload.enabled:true}")                boolean preloadEnabled,
            @Value("${app.preload.page-size:50}")                int preloadPageSize) {

        this.repository     = repository;
        this.preloadEnabled = preloadEnabled;
        this.preloadPageSize = preloadPageSize;

        thumbnailCache = Caffeine.newBuilder()
                .maximumWeight(thumbnailMaxBytes)
                .weigher((Long id, byte[] b) -> b.length)
                .expireAfterAccess(expireMinutes, TimeUnit.MINUTES)
                .softValues()
                .recordStats()
                .build();

        fullImageCache = Caffeine.newBuilder()
                .maximumWeight(fullImageMaxBytes)
                .weigher((Long id, byte[] b) -> b.length)
                .expireAfterAccess(expireMinutes, TimeUnit.MINUTES)
                .softValues()
                .recordStats()
                .build();
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public Optional<byte[]> getThumbnail(long id) {
        byte[] cached = thumbnailCache.get(id, key ->
                repository.findThumbnailById(key).orElse(null));
        return Optional.ofNullable(cached);
    }

    public Optional<byte[]> getFullImage(long id) {
        byte[] cached = fullImageCache.get(id, key ->
                repository.findFullImageById(key).orElse(null));
        return Optional.ofNullable(cached);
    }

    public void invalidateAll() {
        thumbnailCache.invalidateAll();
        fullImageCache.invalidateAll();
        log.info("Image caches invalidated.");
    }

    // ---------------------------------------------------------------
    // Async preload on startup
    // ---------------------------------------------------------------

    // Triggered on ApplicationReadyEvent (not @PostConstruct) so the schema has
    // been created and the spreadsheet imported by DatabaseInitialiser first —
    // otherwise the async preload races ahead and queries a table that does not
    // yet exist ("no such table: images").
    @EventListener(ApplicationReadyEvent.class)
    public void schedulePreload() {
        if (preloadEnabled) {
            preloadThumbnailsAsync();
        }
    }

    @Async
    public void preloadThumbnailsAsync() {
        log.info("Starting async thumbnail preload...");
        List<Long> ids     = repository.findAllIds();
        var loaded         = new AtomicInteger(0);
        int total          = ids.size();

        for (Long id : ids) {
            // Only load if not already cached
            thumbnailCache.get(id, key ->
                    repository.findThumbnailById(key).orElse(null));

            int n = loaded.incrementAndGet();
            if (n % preloadPageSize == 0) {
                log.info("Preloaded {}/{} thumbnails", n, total);
            }
        }

        log.info("Thumbnail preload complete. {} / {} loaded.", loaded.get(), total);
        logStats();
    }

    // ---------------------------------------------------------------
    // Stats
    // ---------------------------------------------------------------

    public void logStats() {
        CacheStats ts = thumbnailCache.stats();
        CacheStats fs = fullImageCache.stats();
        log.info("Thumbnail cache — hitRate={:.1f}% evictions={}",
                ts.hitRate() * 100, ts.evictionCount());
        log.info("Full image cache — hitRate={:.1f}% evictions={}",
                fs.hitRate() * 100, fs.evictionCount());
    }

    // Package-private for testing
    Cache<Long, byte[]> getThumbnailCache() { return thumbnailCache; }
    Cache<Long, byte[]> getFullImageCache()  { return fullImageCache; }
}
