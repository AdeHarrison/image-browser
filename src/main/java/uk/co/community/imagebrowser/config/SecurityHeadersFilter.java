package uk.co.community.imagebrowser.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds baseline security response headers to every request. There's no Spring Security
 * dependency in this project (auth is the hand-rolled admin session in AdminController),
 * so these headers — which Spring Security would otherwise set automatically — are added
 * here instead.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    // htmx is loaded from unpkg (see index.html/admin templates); inline <script>/<style>
    // blocks are used throughout the hand-written templates and HTMX fragments, so both
    // are allowed rather than introducing a nonce/hash pipeline for a two-container app.
    private static final String CSP =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://unpkg.com; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", CSP);
        filterChain.doFilter(request, response);
    }
}
