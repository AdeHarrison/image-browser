package uk.co.community.imagebrowser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.community.imagebrowser.model.ImageSummary;
import uk.co.community.imagebrowser.repository.ImageRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageSearchService")
class ImageSearchServiceTest {

    @Mock
    private ImageRepository repository;

    private ImageSearchService service;

    @BeforeEach
    void setUp() {
        service = new ImageSearchService(repository, 30);
    }

    @Test
    @DisplayName("search delegates to repository with correct offset")
    void search_WhenInvoked_ShouldDelegateToRepositoryWithCorrectOffset() {
        var expected = List.of(summary(1L, "canal.jpg"));
        when(repository.search("canal", 0, 30)).thenReturn(expected);

        var results = service.search("canal", 0);

        assertThat(results).isEqualTo(expected);
        verify(repository).search("canal", 0, 30);
    }

    @Test
    @DisplayName("search with page 2 passes correct offset")
    void search_WhenPageTwo_ShouldPassCorrectOffset() {
        when(repository.search("boat", 30, 30)).thenReturn(List.of());

        service.search("boat", 1);

        verify(repository).search("boat", 30, 30);
    }

    @Test
    @DisplayName("findById delegates to repository")
    void findById_WhenInvoked_ShouldDelegateToRepository() {
        var s = summary(42L, "image.jpg");
        when(repository.findSummaryById(42L)).thenReturn(Optional.of(s));

        var result = service.findById(42L);

        assertThat(result).contains(s);
    }

    @Test
    @DisplayName("findById returns empty when not found")
    void findById_WhenNotFound_ShouldReturnEmpty() {
        when(repository.findSummaryById(99L)).thenReturn(Optional.empty());

        assertThat(service.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getNavigationContext builds correct context")
    void getNavigationContext_WhenMiddleImage_ShouldBuildCorrectContext() {
        when(repository.findFirstId()).thenReturn(Optional.of(1L));
        when(repository.findPrevId(5L)).thenReturn(Optional.of(4L));
        when(repository.findNextId(5L)).thenReturn(Optional.of(6L));
        when(repository.findLastId()).thenReturn(Optional.of(10L));

        var ctx = service.getNavigationContext(5L);

        assertThat(ctx.firstId()).isEqualTo(1L);
        assertThat(ctx.prevId()).isEqualTo(4L);
        assertThat(ctx.nextId()).isEqualTo(6L);
        assertThat(ctx.lastId()).isEqualTo(10L);
        assertThat(ctx.currentId()).isEqualTo(5L);
        assertThat(ctx.hasPrev()).isTrue();
        assertThat(ctx.hasNext()).isTrue();
        assertThat(ctx.isFirst()).isFalse();
        assertThat(ctx.isLast()).isFalse();
    }

    @Test
    @DisplayName("NavigationContext correctly identifies first image")
    void getNavigationContext_WhenFirstImage_ShouldIdentifyAsFirst() {
        when(repository.findFirstId()).thenReturn(Optional.of(1L));
        when(repository.findPrevId(1L)).thenReturn(Optional.empty());
        when(repository.findNextId(1L)).thenReturn(Optional.of(2L));
        when(repository.findLastId()).thenReturn(Optional.of(10L));

        var ctx = service.getNavigationContext(1L);

        assertThat(ctx.isFirst()).isTrue();
        assertThat(ctx.hasPrev()).isFalse();
    }

    @Test
    @DisplayName("NavigationContext correctly identifies last image")
    void getNavigationContext_WhenLastImage_ShouldIdentifyAsLast() {
        when(repository.findFirstId()).thenReturn(Optional.of(1L));
        when(repository.findPrevId(10L)).thenReturn(Optional.of(9L));
        when(repository.findNextId(10L)).thenReturn(Optional.empty());
        when(repository.findLastId()).thenReturn(Optional.of(10L));

        var ctx = service.getNavigationContext(10L);

        assertThat(ctx.isLast()).isTrue();
        assertThat(ctx.hasNext()).isFalse();
    }

    @Test
    @DisplayName("count delegates to repository")
    void count_WhenInvoked_ShouldDelegateToRepository() {
        when(repository.count()).thenReturn(42L);
        assertThat(service.count()).isEqualTo(42L);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ImageSummary summary(long id, String filename) {
        return new ImageSummary(id, filename, "Sheet1", "R0C0", 300, 200, "", "");
    }
}
