package uk.co.community.imagebrowser.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.community.imagebrowser.TestCacheConfig;
import uk.co.community.imagebrowser.admin.AdminPasswordService;
import uk.co.community.imagebrowser.admin.AdminSessionManager;
import uk.co.community.imagebrowser.service.SpreadsheetImportService;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(TestCacheConfig.class)
@DisplayName("AdminController")
class AdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AdminSessionManager    sessionManager;
    @MockitoBean private AdminPasswordService   passwordService;
    @MockitoBean private SpreadsheetImportService importService;

    @Test
    @DisplayName("GET /admin/login returns login page")
    void loginPage_WhenRequested_ShouldReturn200WithLoginView() throws Exception {
        mvc.perform(get("/admin/login"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/login"));
    }

    @Test
    @DisplayName("POST /admin/login with wrong password returns error fragment")
    void login_WhenPasswordIncorrect_ShouldReturnErrorFragment() throws Exception {
        when(passwordService.verify("wrong")).thenReturn(false);

        mvc.perform(post("/admin/login").param("password", "wrong"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Incorrect password")));
    }

    @Test
    @DisplayName("GET /admin/login redirects to panel when already authenticated")
    void loginPage_WhenAlreadyAuthenticated_ShouldRedirectToAdmin() throws Exception {
        when(sessionManager.isActiveSession(any())).thenReturn(true);

        mvc.perform(get("/admin/login").sessionAttr("admin", true))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("POST /admin/login when admin already logged in returns error fragment")
    void login_WhenAdminAlreadyLoggedIn_ShouldReturnErrorFragment() throws Exception {
        when(passwordService.verify("correct")).thenReturn(true);
        when(sessionManager.isAdminLoggedIn()).thenReturn(true);

        mvc.perform(post("/admin/login").param("password", "correct"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("already logged in")));
    }

    @Test
    @DisplayName("POST /admin/login with correct password and free slot succeeds")
    void login_WhenPasswordCorrectAndSlotFree_ShouldRedirectToAdmin() throws Exception {
        when(passwordService.verify("correct")).thenReturn(true);
        when(sessionManager.isAdminLoggedIn()).thenReturn(false);
        when(sessionManager.login(any())).thenReturn(true);

        mvc.perform(post("/admin/login").param("password", "correct"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/admin")));
    }

    @Test
    @DisplayName("POST /admin/login when the session cannot be claimed returns error")
    void login_WhenSlotCannotBeClaimed_ShouldReturnError() throws Exception {
        when(passwordService.verify("correct")).thenReturn(true);
        when(sessionManager.isAdminLoggedIn()).thenReturn(false);
        when(sessionManager.login(any())).thenReturn(false);

        mvc.perform(post("/admin/login").param("password", "correct"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("could not be claimed")));
    }

    @Test
    @DisplayName("GET /admin without session redirects to login")
    void adminPanel_WhenNoSession_ShouldRedirectToLogin() throws Exception {
        mvc.perform(get("/admin"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("GET /admin with a valid admin session returns the panel view")
    void adminPanel_WhenAuthorised_ShouldReturnPanelView() throws Exception {
        when(sessionManager.isActiveSession(any())).thenReturn(true);

        mvc.perform(get("/admin").sessionAttr("admin", true))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/panel"));
    }

    @Test
    @DisplayName("POST /admin/reload without session returns error")
    void reload_WhenNoSession_ShouldReturnNotAuthorised() throws Exception {
        mvc.perform(post("/admin/reload"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Not authorised")));
    }

    @Test
    @DisplayName("POST /admin/reload denies when the session lacks the admin flag")
    void reload_WhenSessionLacksAdminFlag_ShouldReturnNotAuthorised() throws Exception {
        mvc.perform(post("/admin/reload").sessionAttr("other", "x"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Not authorised")));
    }

    @Test
    @DisplayName("POST /admin/reload denies when the session is not the active admin")
    void reload_WhenSessionNotActive_ShouldReturnNotAuthorised() throws Exception {
        when(sessionManager.isActiveSession(any())).thenReturn(false);

        mvc.perform(post("/admin/reload").sessionAttr("admin", true))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Not authorised")));
    }

    @Test
    @DisplayName("POST /admin/reload when authorised triggers a force import and reports success")
    void reload_WhenAuthorised_ShouldForceImportAndReturnSuccess() throws Exception {
        when(sessionManager.isActiveSession(any())).thenReturn(true);
        when(importService.forceImport()).thenReturn(
                new SpreadsheetImportService.ImportResult(
                        SpreadsheetImportService.ImportResult.Status.SUCCESS, 7, "v2",
                        "7 images imported successfully."));

        mvc.perform(post("/admin/reload").sessionAttr("admin", true))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("7 images imported")));
    }

    @Test
    @DisplayName("POST /admin/reload returns an error fragment when the import throws")
    void reload_WhenImportThrows_ShouldReturnErrorFragment() throws Exception {
        when(sessionManager.isActiveSession(any())).thenReturn(true);
        when(importService.forceImport()).thenThrow(new IOException("disk gone"));

        mvc.perform(post("/admin/reload").sessionAttr("admin", true))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Reload failed")));
    }

    @Test
    @DisplayName("POST /admin/logout without session redirects to /")
    void logout_WhenNoSession_ShouldRedirectToRoot() throws Exception {
        mvc.perform(post("/admin/logout"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /admin/logout with a session releases the admin lock and redirects to /")
    void logout_WhenSessionPresent_ShouldReleaseLockAndRedirect() throws Exception {
        mvc.perform(post("/admin/logout").sessionAttr("admin", true))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/"));

        verify(sessionManager).logout(any());
    }
}
