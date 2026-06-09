package uk.co.community.imagebrowser.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import uk.co.community.imagebrowser.admin.AdminSessionManager;

@Configuration
public class SessionConfig {

    private static final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    private static final int SESSION_TIMEOUT_SECONDS = 30 * 60; // 30 minutes

    /**
     * Release admin lock when session expires (e.g. browser closed without logout).
     */
    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> sessionListener(
            AdminSessionManager adminSessionManager) {

        var bean = new ServletListenerRegistrationBean<HttpSessionListener>();
        bean.setListener(new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent e) {
                e.getSession().setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
            }
            @Override
            public void sessionDestroyed(HttpSessionEvent e) {
                String sessionId = e.getSession().getId();
                adminSessionManager.forceLogout(sessionId);
                log.debug("Session destroyed, admin lock released if held: {}", sessionId);
            }
        });
        return bean;
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        var serializer = new DefaultCookieSerializer();
        serializer.setSameSite("Strict");
        serializer.setUseHttpOnlyCookie(true);
        return serializer;
    }
}
