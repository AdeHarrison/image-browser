package uk.co.community.imagebrowser.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.repository.ImageRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageCacheService")
class ImageCacheServiceTest {

    @Mock private ImageRepository repository;

    private ImageCacheService newService(boolean preloadEnabled, int preloadPageSize) {
        return new ImageCacheService(
                repository, 15_728_640L, 41_943_040L, 15, preloadEnabled, preloadPageSize);
    }

    // ---------------------------------------------------------------
    // getThumbnail / getFullImage
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getThumbnail returns bytes loaded from the repository")
    void getThumbnail_WhenRepositoryHasBytes_ShouldReturnBytes() {
        var svc = newService(false, 50);
        when(repository.findThumbnailById(1L)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        assertThat(svc.getThumbnail(1L)).contains(new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("getThumbnail caches the value so the repository is queried once")
    void getThumbnail_WhenCalledTwice_ShouldQueryRepositoryOnce() {
        var svc = newService(false, 50);
        when(repository.findThumbnailById(1L)).thenReturn(Optional.of(new byte[]{1}));

        svc.getThumbnail(1L);
        svc.getThumbnail(1L);

        verify(repository, times(1)).findThumbnailById(1L);
    }

    @Test
    @DisplayName("getThumbnail returns empty when the repository has nothing")
    void getThumbnail_WhenRepositoryEmpty_ShouldReturnEmpty() {
        var svc = newService(false, 50);
        when(repository.findThumbnailById(99L)).thenReturn(Optional.empty());

        assertThat(svc.getThumbnail(99L)).isEmpty();
    }

    @Test
    @DisplayName("getFullImage returns bytes loaded from the repository")
    void getFullImage_WhenRepositoryHasBytes_ShouldReturnBytes() {
        var svc = newService(false, 50);
        when(repository.findFullImageById(1L)).thenReturn(Optional.of(new byte[]{9, 8, 7}));

        assertThat(svc.getFullImage(1L)).contains(new byte[]{9, 8, 7});
    }

    @Test
    @DisplayName("getFullImage returns empty when the repository has nothing")
    void getFullImage_WhenRepositoryEmpty_ShouldReturnEmpty() {
        var svc = newService(false, 50);
        when(repository.findFullImageById(99L)).thenReturn(Optional.empty());

        assertThat(svc.getFullImage(99L)).isEmpty();
    }

    // ---------------------------------------------------------------
    // invalidateAll
    // ---------------------------------------------------------------

    @Test
    @DisplayName("invalidateAll clears both caches so the repository is queried again")
    void invalidateAll_WhenCalled_ShouldClearBothCaches() {
        var svc = newService(false, 50);
        when(repository.findThumbnailById(1L)).thenReturn(Optional.of(new byte[]{1}));
        when(repository.findFullImageById(1L)).thenReturn(Optional.of(new byte[]{2}));
        svc.getThumbnail(1L);
        svc.getFullImage(1L);

        svc.invalidateAll();
        svc.getThumbnail(1L);
        svc.getFullImage(1L);

        verify(repository, times(2)).findThumbnailById(1L);
        verify(repository, times(2)).findFullImageById(1L);
    }

    // ---------------------------------------------------------------
    // Preload
    // ---------------------------------------------------------------

    @Test
    @DisplayName("schedulePreload loads every thumbnail when preload is enabled")
    void schedulePreload_WhenEnabled_ShouldPreloadAllThumbnails() {
        var svc = newService(true, 2);   // small page size to exercise the progress-log branch
        when(repository.findAllIds()).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(repository.findThumbnailById(anyLong())).thenReturn(Optional.of(new byte[]{1}));

        svc.schedulePreload();

        verify(repository).findAllIds();
        verify(repository, times(4)).findThumbnailById(anyLong());
    }

    @Test
    @DisplayName("schedulePreload does nothing when preload is disabled")
    void schedulePreload_WhenDisabled_ShouldDoNothing() {
        var svc = newService(false, 50);

        svc.schedulePreload();

        verifyNoInteractions(repository);
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------

    @Test
    @DisplayName("cache accessors expose the underlying caches")
    void cacheAccessors_WhenCalled_ShouldReturnNonNullCaches() {
        var svc = newService(false, 50);

        assertThat(svc.getThumbnailCache()).isNotNull();
        assertThat(svc.getFullImageCache()).isNotNull();
    }
}
