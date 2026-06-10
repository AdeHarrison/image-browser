package uk.co.community.imagebrowser.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.community.imagebrowser.TestCacheConfig;
import uk.co.community.imagebrowser.model.ImageSummary;
import uk.co.community.imagebrowser.service.ImageCacheService;
import uk.co.community.imagebrowser.service.ImageSearchService;
import uk.co.community.imagebrowser.service.ImageSearchService.NavigationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageController.class)
@Import(TestCacheConfig.class)
@DisplayName("ImageController")
class ImageControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private ImageSearchService searchService;
    @MockitoBean private ImageCacheService  cacheService;

    @BeforeEach
    void setUp() {
        when(searchService.count()).thenReturn(42L);
    }

    @Test
    @DisplayName("GET / returns 200 with index view")
    void index_WhenRequested_ShouldReturn200WithIndexView() throws Exception {
        mvc.perform(get("/"))
           .andExpect(status().isOk())
           .andExpect(view().name("index"))
           .andExpect(model().attribute("totalImages", 42L));
    }

    @Test
    @DisplayName("GET /search returns grid HTML fragment")
    void search_WhenQueryMatches_ShouldReturnGridFragment() throws Exception {
        var results = List.of(
                new ImageSummary(1L, "canal.jpg", "Sheet1", "R0C0", 300, 200, "", ""));
        when(searchService.search("canal", 0)).thenReturn(results);

        mvc.perform(get("/search").param("q", "canal"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("canal.jpg")));
    }

    @Test
    @DisplayName("GET /search with empty query returns all images")
    void search_WhenQueryEmpty_ShouldReturnAllImages() throws Exception {
        when(searchService.search("", 0)).thenReturn(List.of());

        mvc.perform(get("/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("No images found")));
    }

    @Test
    @DisplayName("GET /search escapes HTML in filename to prevent XSS")
    void search_WhenFilenameContainsHtml_ShouldEscapeToPreventXss() throws Exception {
        var results = List.of(
                new ImageSummary(1L, "<script>alert('xss')</script>",
                        "Sheet1", "R0C0", 300, 200, "", ""));
        when(searchService.search("script", 0)).thenReturn(results);

        mvc.perform(get("/search").param("q", "script"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("<script>"))))
           .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    @DisplayName("GET /search with a full page emits an infinite-scroll load-more trigger")
    void search_WhenPageFull_ShouldEmitLoadMoreTrigger() throws Exception {
        var results = new ArrayList<ImageSummary>();
        for (int i = 0; i < 30; i++) {
            results.add(new ImageSummary(i, "a-very-long-filename-" + i + ".jpg",
                    "Sheet1", "R0C0", 300, 200, "", ""));
        }
        when(searchService.search("boat", 0)).thenReturn(results);

        mvc.perform(get("/search").param("q", "boat"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("load-more")))
           .andExpect(content().string(containsString("page=1")))
           .andExpect(content().string(containsString("…")));  // long filename truncated
    }

    @Test
    @DisplayName("GET /search tolerates null filename and tags without erroring")
    void search_WhenFieldsNull_ShouldRenderWithoutError() throws Exception {
        var results = List.of(
                new ImageSummary(1L, null, "Sheet1", "R0C0", 0, 0, null, null));
        when(searchService.search("x", 0)).thenReturn(results);

        mvc.perform(get("/search").param("q", "x"))
           .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /image/{id}/modal returns modal HTML for known id")
    void modal_WhenIdKnown_ShouldReturnModalHtml() throws Exception {
        var summary = new ImageSummary(5L, "test.jpg", "Sheet1", "R1C1",
                200, 150, "tag1", "A test image");
        when(searchService.findById(5L)).thenReturn(Optional.of(summary));

        mvc.perform(get("/image/5/modal"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("test.jpg")))
           .andExpect(content().string(containsString("Sheet1")))
           .andExpect(content().string(containsString("tag1")));
    }

    @Test
    @DisplayName("GET /image/{id}/modal returns not found message for unknown id")
    void modal_WhenIdUnknown_ShouldReturnNotFoundMessage() throws Exception {
        when(searchService.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(get("/image/999/modal"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("not found")));
    }

    @Test
    @DisplayName("GET /image/{id}/viewer renders viewer with navigation for a middle image")
    void viewer_WhenMiddleImage_ShouldRenderViewerWithNavigation() throws Exception {
        var summary = new ImageSummary(5L, "photo.jpg", "Sheet1", "R1C1", 200, 150, "tag", "desc");
        when(searchService.findById(5L)).thenReturn(Optional.of(summary));
        when(searchService.getNavigationContext(5L))
                .thenReturn(new NavigationContext(1L, 4L, 6L, 10L, 5L));

        mvc.perform(get("/image/5/viewer"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/image/5/bytes")))
           .andExpect(content().string(containsString("photo.jpg")));
    }

    @Test
    @DisplayName("GET /image/{id}/viewer disables nav when image is both first and last")
    void viewer_WhenOnlyImage_ShouldDisableNavButtons() throws Exception {
        var summary = new ImageSummary(5L, "only.jpg", "Sheet1", "R1C1", 200, 150, "", "");
        when(searchService.findById(5L)).thenReturn(Optional.of(summary));
        // first and last both point to current; no prev/next
        when(searchService.getNavigationContext(5L))
                .thenReturn(new NavigationContext(5L, null, null, 5L, 5L));

        mvc.perform(get("/image/5/viewer"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("disabled")));
    }

    @Test
    @DisplayName("GET /image/{id}/viewer falls back to current id when navigation ids are null")
    void viewer_WhenNavigationIdsNull_ShouldFallBackToCurrentId() throws Exception {
        var summary = new ImageSummary(5L, "x.jpg", "Sheet1", "R1C1", 200, 150, "", "");
        when(searchService.findById(5L)).thenReturn(Optional.of(summary));
        when(searchService.getNavigationContext(5L))
                .thenReturn(new NavigationContext(null, null, null, null, 5L));

        mvc.perform(get("/image/5/viewer"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("x.jpg")));
    }

    @Test
    @DisplayName("GET /image/{id}/viewer returns not found message for unknown id")
    void viewer_WhenIdUnknown_ShouldReturnNotFoundMessage() throws Exception {
        when(searchService.getNavigationContext(999L))
                .thenReturn(new NavigationContext(null, null, null, null, 999L));
        when(searchService.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(get("/image/999/viewer"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("not found")));
    }

    @Test
    @DisplayName("GET /thumbnail/{id} returns 200 with image bytes")
    void thumbnail_WhenIdKnown_ShouldReturn200WithImageBytes() throws Exception {
        when(cacheService.getThumbnail(1L)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        mvc.perform(get("/thumbnail/1"))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/jpeg"))
           .andExpect(header().string("Cache-Control", containsString("max-age")));
    }

    @Test
    @DisplayName("GET /thumbnail/{id} returns 404 when not found")
    void thumbnail_WhenIdUnknown_ShouldReturn404() throws Exception {
        when(cacheService.getThumbnail(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/thumbnail/99"))
           .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /image/{id}/bytes returns 200 with image bytes")
    void fullImage_WhenIdKnown_ShouldReturn200WithImageBytes() throws Exception {
        when(cacheService.getFullImage(1L)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        mvc.perform(get("/image/1/bytes"))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    @DisplayName("GET /image/{id}/bytes returns 404 when not found")
    void fullImage_WhenIdUnknown_ShouldReturn404() throws Exception {
        when(cacheService.getFullImage(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/image/99/bytes"))
           .andExpect(status().isNotFound());
    }
}
