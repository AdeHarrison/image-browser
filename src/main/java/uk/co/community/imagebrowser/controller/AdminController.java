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
import uk.co.community.imagebrowser.service.SpreadsheetImportService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final String ADMIN_ATTR = "admin";

    private final AdminSessionManager    sessionManager;
    private final AdminPasswordService   passwordService;
    private final SpreadsheetImportService importService;

    public AdminController(AdminSessionManager    sessionManager,
                           AdminPasswordService   passwordService,
                           SpreadsheetImportService importService) {
        this.sessionManager  = sessionManager;
        this.passwordService = passwordService;
        this.importService   = importService;
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

    @PostMapping("/reload")
    @ResponseBody
    public String reload(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return "<p class='error'>Not authorised.</p>";
        }
        try {
            var result = importService.forceImport();
            return "<p class='success'>✓ %s</p>".formatted(result.message());
        } catch (Exception e) {
            log.error("Admin reload failed: {}", e.getMessage(), e);
            return "<p class='error'>Reload failed: %s</p>".formatted(e.getMessage());
        }
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
