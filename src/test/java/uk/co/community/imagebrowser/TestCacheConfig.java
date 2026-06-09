package uk.co.community.imagebrowser;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * Supplies a no-op {@link CacheManager} for sliced tests (e.g. {@code @WebMvcTest}).
 *
 * The application class carries {@code @EnableCaching}, which requires a CacheManager
 * bean, but slice tests do not load {@code CacheAutoConfiguration}. The full-context
 * integration test gets its CacheManager from auto-configuration instead.
 *
 * Note: the Spring cache abstraction is otherwise unused here — real image caching is
 * the manual Caffeine in {@code ImageCacheService}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestCacheConfig {

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}