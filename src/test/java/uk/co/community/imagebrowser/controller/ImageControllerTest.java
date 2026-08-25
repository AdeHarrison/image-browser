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
import uk.co.community.imagebrowser.model.AviationImageSummary;
import uk.co.community.imagebrowser.service.AviationImageService;

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

    @MockitoBean private AviationImageService imageService;

    @BeforeEach
    void setUp() {
        when(imageService.count()).thenReturn(42L);
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
    @DisplayName("GET /search returns grid HTML fragment with thumbnail and folder")
    void search_WhenQueryMatches_ShouldReturnGridFragment() throws Exception {
        var results = List.of(
                new AviationImageSummary(1L, "AVIATION", "4", "3.jpg", "Mr Hucks publicity photograph"));
        when(imageService.search("hucks")).thenReturn(results);

        mvc.perform(get("/search").param("q", "hucks"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/thumbnail/1")))
           .andExpect(content().string(containsString("Folder: 4")));
    }

    @Test
    @DisplayName("GET /search with empty query returns all images")
    void search_WhenQueryEmpty_ShouldReturnAllImages() throws Exception {
        when(imageService.search("")).thenReturn(List.of());

        mvc.perform(get("/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("No images found")));
    }

    @Test
    @DisplayName("GET /search escapes HTML in description to prevent XSS")
    void search_WhenDescriptionContainsHtml_ShouldEscapeToPreventXss() throws Exception {
        var results = List.of(
                new AviationImageSummary(1L, "AVIATION", "4", "3.jpg", "<script>alert('xss')</script>"));
        when(imageService.search("script")).thenReturn(results);

        mvc.perform(get("/search").param("q", "script"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("<script>"))))
           .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    @DisplayName("GET /search tolerates null description without erroring")
    void search_WhenDescriptionNull_ShouldRenderWithoutError() throws Exception {
        var results = List.of(new AviationImageSummary(1L, "AVIATION", "4", "3.jpg", null));
        when(imageService.search("x")).thenReturn(results);

        mvc.perform(get("/search").param("q", "x"))
           .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /browse returns grid HTML fragment plus first/prev/next/last nav")
    void browse_WhenPageHasResults_ShouldReturnGridAndNav() throws Exception {
        var results = List.of(
                new AviationImageSummary(1L, "AVIATION", "4", "3.jpg", "Mr Hucks publicity photograph"));
        when(imageService.browsePageSize()).thenReturn(60);
        when(imageService.count()).thenReturn(1L);
        when(imageService.browse(0)).thenReturn(results);

        mvc.perform(get("/browse").param("page", "0"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/thumbnail/1")))
           .andExpect(content().string(containsString("id=\"browse-nav\"")))
           .andExpect(content().string(containsString("First")))
           .andExpect(content().string(containsString("Previous")))
           .andExpect(content().string(containsString("Next")))
           .andExpect(content().string(containsString("Last")));
    }

    @Test
    @DisplayName("GET /browse clamps an out-of-range page to the last page")
    void browse_WhenPageOutOfRange_ShouldClampToLastPage() throws Exception {
        when(imageService.browsePageSize()).thenReturn(60);
        when(imageService.count()).thenReturn(120L);
        when(imageService.browse(1)).thenReturn(List.of(
                new AviationImageSummary(61L, "AVIATION", "5", "1.jpg", "desc")));

        mvc.perform(get("/browse").param("page", "99"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/thumbnail/61")))
           .andExpect(content().string(containsString("Page 2 of 2")));
    }

    @Test
    @DisplayName("GET /thumbnail/{id} returns 200 with image bytes")
    void thumbnail_WhenIdKnown_ShouldReturn200WithImageBytes() throws Exception {
        when(imageService.getThumbnail(1L)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        mvc.perform(get("/thumbnail/1"))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/jpeg"))
           .andExpect(header().string("Cache-Control", containsString("max-age")));
    }

    @Test
    @DisplayName("GET /thumbnail/{id} returns 404 when not found")
    void thumbnail_WhenIdUnknown_ShouldReturn404() throws Exception {
        when(imageService.getThumbnail(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/thumbnail/99"))
           .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /image/{id} returns 200 with full image bytes and content type from filename")
    void fullImage_WhenIdKnown_ShouldReturn200WithImageBytes() throws Exception {
        when(imageService.findById(1L)).thenReturn(Optional.of(
                new AviationImageSummary(1L, "AVIATION", "4", "3.png", "desc")));
        when(imageService.getFullImage(1L)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        mvc.perform(get("/image/1"))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/png"))
           .andExpect(header().string("Cache-Control", containsString("max-age")));
    }

    @Test
    @DisplayName("GET /image/{id} returns 404 when the record is unknown")
    void fullImage_WhenIdUnknown_ShouldReturn404() throws Exception {
        when(imageService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/image/99"))
           .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /image/{id} returns 404 when the file is missing on disk")
    void fullImage_WhenFileMissing_ShouldReturn404() throws Exception {
        when(imageService.findById(1L)).thenReturn(Optional.of(
                new AviationImageSummary(1L, "AVIATION", "4", "3.jpg", "desc")));
        when(imageService.getFullImage(1L)).thenReturn(Optional.empty());

        mvc.perform(get("/image/1"))
           .andExpect(status().isNotFound());
    }
}
