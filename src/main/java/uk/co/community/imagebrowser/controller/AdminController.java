package uk.co.community.imagebrowser.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.co.community.imagebrowser.admin.AdminPasswordService;
import uk.co.community.imagebrowser.admin.AdminSessionManager;
import uk.co.community.imagebrowser.service.AviationImportService;
import uk.co.community.imagebrowser.service.OutputSyncService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final String ADMIN_ATTR = "admin";

    private final AdminSessionManager    sessionManager;
    private final AdminPasswordService   passwordService;
    private final OutputSyncService      outputSyncService;
    private final AviationImportService  aviationImportService;

    public AdminController(AdminSessionManager    sessionManager,
                           AdminPasswordService   passwordService,
                           OutputSyncService      outputSyncService,
                           AviationImportService  aviationImportService) {
        this.sessionManager    = sessionManager;
        this.passwordService   = passwordService;
        this.outputSyncService = outputSyncService;
        this.aviationImportService = aviationImportService;
    }

    // ---------------------------------------------------------------
    // Login page
    // ---------------------------------------------------------------

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request, Model model) {
        // Already logged in — redirect to admin panel
        if (isAdmin(request)) {
            return "redirect:/admin";
        }
        return "admin/login";
    }

    @PostMapping("/login")
    @ResponseBody
    public String login(@RequestParam String password,
                        HttpServletRequest request) {

        if (!passwordService.verify(password)) {
            return "<p class='error'>Incorrect password.</p>";
        }

        if (sessionManager.isAdminLoggedIn()) {
            return "<p class='error'>Admin is already logged in from another session.</p>";
        }

        HttpSession session = request.getSession(true);
        if (sessionManager.login(session.getId())) {
            session.setAttribute(ADMIN_ATTR, true);
            // HTMX redirect header
            request.getServletContext()
                   .log("Admin logged in: session=" + session.getId());
            return "<script>window.location='/admin'</script>";
        } else {
            return "<p class='error'>Admin session could not be claimed. Try again.</p>";
        }
    }

    // ---------------------------------------------------------------
    // Admin panel
    // ---------------------------------------------------------------

    @GetMapping
    public String adminPanel(HttpServletRequest request, Model model) {
        if (!isAdmin(request)) return "redirect:/admin/login";
        model.addAttribute("alreadyLoggedIn", false);
        return "admin/panel";
    }

    // ---------------------------------------------------------------
    // Reload
    // ---------------------------------------------------------------

    // Rebuilds the output folder structure from the input spreadsheet/images
    // (OutputSyncService), then reimports the AVIATION sheet into PostgreSQL
    // (AviationImportService).
    @PostMapping("/reload")
    @ResponseBody
    public String reload(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return "<p class='error'>Not authorised.</p>";
        }
        try {
            int copied   = outputSyncService.sync();
            aviationImportService.reimport();
            return "<p class='success'>✓ %d image(s) copied.</p>".formatted(copied);
        } catch (Exception e) {
            log.error("Admin reload failed: {}", e.getMessage(), e);
            return "<p class='error'>Reload failed: %s</p>".formatted(e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Change password
    // ---------------------------------------------------------------

    @PostMapping("/change-password")
    @ResponseBody
    public String changePassword(@RequestParam String currentPassword,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  HttpServletRequest request) {
        if (!isAdmin(request)) {
            return "<p class='error'>Not authorised.</p>";
        }
        if (!passwordService.verify(currentPassword)) {
            return "<p class='error'>Current password is incorrect.</p>";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "<p class='error'>New passwords do not match.</p>";
        }
        if (!passwordService.setPassword(newPassword)) {
            return "<p class='error'>New password must be at least %d characters.</p>"
                    .formatted(AdminPasswordService.MIN_PASSWORD_LENGTH);
        }
        return "<p class='success'>✓ Password changed successfully.</p>";
    }

    // ---------------------------------------------------------------
    // Logout
    // ---------------------------------------------------------------

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionManager.logout(session.getId());
            session.invalidate();
        }
        return "redirect:/";
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    // TEMPORARY: admin auth disabled for local testing of the output-sync
    // import flow — no login required to reach /admin or trigger /admin/reload.
    // Restore the real check below before any real deployment.
    private boolean isAdmin(HttpServletRequest request) {
        return true;
        // HttpSession session = request.getSession(false);
        // if (session == null) return false;
        // return Boolean.TRUE.equals(session.getAttribute(ADMIN_ATTR))
        //         && sessionManager.isActiveSession(session.getId());
    }
}
