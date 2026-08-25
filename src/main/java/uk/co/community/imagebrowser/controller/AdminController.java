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
import uk.co.community.imagebrowser.service.AdminReloadService;
import uk.co.community.imagebrowser.service.ImportProgressService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final String ADMIN_ATTR = "admin";

    // How often the UI polls /admin/reload/progress while a reload is running.
    private static final int PROGRESS_POLL_DELAY_MS = 600;

    private final AdminSessionManager    sessionManager;
    private final AdminPasswordService   passwordService;
    private final AdminReloadService     reloadService;
    private final ImportProgressService  progressService;

    public AdminController(AdminSessionManager    sessionManager,
                           AdminPasswordService   passwordService,
                           AdminReloadService     reloadService,
                           ImportProgressService  progressService) {
        this.sessionManager    = sessionManager;
        this.passwordService   = passwordService;
        this.reloadService     = reloadService;
        this.progressService   = progressService;
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
    // (AviationImportService). Runs on a background thread (AdminReloadService)
    // so this request returns immediately with a progress bar that polls
    // /admin/reload/progress until the reload finishes.
    @PostMapping("/reload")
    @ResponseBody
    public String reload(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return "<p class='error'>Not authorised.</p>";
        }
        if (!progressService.tryStart()) {
            return "<p class='error'>A reload is already in progress.</p>";
        }
        reloadService.runReload();
        return renderProgress(progressService.snapshot());
    }

    @GetMapping("/reload/progress")
    @ResponseBody
    public String reloadProgress(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return "<p class='error'>Not authorised.</p>";
        }
        return renderProgress(progressService.snapshot());
    }

    private String renderProgress(ImportProgressService.Snapshot snap) {
        return switch (snap.status()) {
            case RUNNING -> """
                <div class="reload-progress" hx-get="/admin/reload/progress"
                     hx-trigger="load delay:%dms" hx-swap="outerHTML">
                    <p class="progress-stage">%s</p>
                    <div class="progress-bar"><div class="progress-bar-fill" style="width:%d%%"></div></div>
                    <p class="progress-percent">%d%%</p>
                </div>
                """.formatted(PROGRESS_POLL_DELAY_MS, escapeHtml(snap.stage()), snap.percent(), snap.percent());
            case SUCCESS -> "<p class='success'>✓ %s</p>".formatted(escapeHtml(snap.message()));
            case ERROR   -> "<p class='error'>Reload failed: %s</p>".formatted(escapeHtml(snap.message()));
            case IDLE    -> "";
        };
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        return Boolean.TRUE.equals(session.getAttribute(ADMIN_ATTR))
                && sessionManager.isActiveSession(session.getId());
    }
}
