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

import static org.hamcrest.Matchers.containsString;
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
    void loginPageReturns200() throws Exception {
        mvc.perform(get("/admin/login"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/login"));
    }

    @Test
    @DisplayName("POST /admin/login with wrong password returns error fragment")
    void loginWrongPassword() throws Exception {
        when(passwordService.verify("wrong")).thenReturn(false);

        mvc.perform(post("/admin/login").param("password", "wrong"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Incorrect password")));
    }

    @Test
    @DisplayName("POST /admin/login when admin already logged in returns error fragment")
    void loginAlreadyActive() throws Exception {
        when(passwordService.verify("correct")).thenReturn(true);
        when(sessionManager.isAdminLoggedIn()).thenReturn(true);

        mvc.perform(post("/admin/login").param("password", "correct"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("already logged in")));
    }

    @Test
    @DisplayName("POST /admin/login with correct password and free slot succeeds")
    void loginSuccess() throws Exception {
        when(passwordService.verify("correct")).thenReturn(true);
        when(sessionManager.isAdminLoggedIn()).thenReturn(false);
        when(sessionManager.login(any())).thenReturn(true);

        mvc.perform(post("/admin/login").param("password", "correct"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/admin")));
    }

    @Test
    @DisplayName("GET /admin without session redirects to login")
    void adminPanelNoSession() throws Exception {
        mvc.perform(get("/admin"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("POST /admin/reload without session returns error")
    void reloadNoSession() throws Exception {
        mvc.perform(post("/admin/reload"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Not authorised")));
    }

    @Test
    @DisplayName("POST /admin/logout invalidates session and redirects to /")
    void logout() throws Exception {
        mvc.perform(post("/admin/logout"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/"));
    }
}
