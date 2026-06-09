package uk.co.community.imagebrowser.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.community.imagebrowser.repository.ImageRepository;
import uk.co.community.imagebrowser.model.ImageRecord;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full Spring context integration tests using in-memory SQLite.
 * Profile: test (see application-test.properties)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Application Integration Tests")
class ApplicationIntegrationIT {

    @Autowired private MockMvc         mvc;
    @Autowired private ImageRepository repository;

    @Test
    @DisplayName("application context loads successfully")
    void Application_SpringContext_WhenStarted_ShouldLoadSuccessfully() {
        // If the context fails to load, this test fails
    }

    @Test
    @DisplayName("GET / returns 200 OK")
    void IndexController_View_WhenRootRequested_ShouldReturn200() throws Exception {
        mvc.perform(get("/"))
           .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /search with no query returns grid fragment")
    void SearchController_Repository_WhenNoQuery_ShouldReturnGridFragment() throws Exception {
        insertTestImage("canal-boat.jpg", "canal", "narrow boat on canal");
        insertTestImage("windmill.jpg", "windmill", "old windmill in field");

        mvc.perform(get("/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(content().string(anyOf(
               containsString("canal-boat.jpg"),
               containsString("No images"))));
    }

    @Test
    @DisplayName("GET /search with query filters results")
    void SearchController_Repository_WhenQueryGiven_ShouldFilterResults() throws Exception {
        insertTestImage("canal-boat.jpg", "canal", "narrow boat");
        insertTestImage("windmill.jpg", "windmill", "windmill");

        mvc.perform(get("/search").param("q", "canal"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("canal-boat.jpg")))
           .andExpect(content().string(not(containsString("windmill.jpg"))));
    }

    @Test
    @DisplayName("GET /thumbnail/{id} returns 404 for unknown id")
    void ThumbnailController_Repository_WhenIdUnknown_ShouldReturn404() throws Exception {
        mvc.perform(get("/thumbnail/99999"))
           .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /thumbnail/{id} returns 200 for known id")
    void ThumbnailController_Repository_WhenIdKnown_ShouldReturn200() throws Exception {
        long id = insertTestImage("test.jpg", "test", "a test image");

        mvc.perform(get("/thumbnail/" + id))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    @DisplayName("GET /image/{id}/modal returns metadata")
    void ModalController_Repository_WhenIdKnown_ShouldReturnMetadata() throws Exception {
        long id = insertTestImage("heritage.jpg", "heritage", "heritage site photo");

        mvc.perform(get("/image/" + id + "/modal"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("heritage.jpg")));
    }

    @Test
    @DisplayName("GET /admin/login returns login page")
    void AdminController_LoginView_WhenLoginRequested_ShouldReturnLoginPage() throws Exception {
        mvc.perform(get("/admin/login"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/login"));
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private long insertTestImage(String filename, String tags, String description) {
        // Minimal valid JPEG header
        byte[] jpeg = new byte[]{
            (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
        };
        return repository.insert(new ImageRecord(
                0L, filename, "Sheet1", "R0C0",
                "image/jpeg", jpeg, jpeg,
                100, 100, tags, description));
    }
}
