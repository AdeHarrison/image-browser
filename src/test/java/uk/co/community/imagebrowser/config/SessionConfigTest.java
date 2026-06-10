package uk.co.community.imagebrowser.config;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.co.community.imagebrowser.admin.AdminSessionManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SessionConfig")
class SessionConfigTest {

    private final SessionConfig config = new SessionConfig();

    @Test
    @DisplayName("sessionCreated applies the configured inactivity timeout")
    void sessionCreated_WhenInvoked_ShouldSetMaxInactiveInterval() {
        HttpSessionListener listener =
                config.sessionListener(mock(AdminSessionManager.class)).getListener();
        var session = mock(HttpSession.class);
        var event   = mock(HttpSessionEvent.class);
        when(event.getSession()).thenReturn(session);

        listener.sessionCreated(event);

        verify(session).setMaxInactiveInterval(30 * 60);
    }

    @Test
    @DisplayName("sessionDestroyed releases the admin lock for the expiring session")
    void sessionDestroyed_WhenInvoked_ShouldForceLogout() {
        var manager = mock(AdminSessionManager.class);
        HttpSessionListener listener = config.sessionListener(manager).getListener();
        var session = mock(HttpSession.class);
        when(session.getId()).thenReturn("sess-1");
        var event = mock(HttpSessionEvent.class);
        when(event.getSession()).thenReturn(session);

        listener.sessionDestroyed(event);

        verify(manager).forceLogout("sess-1");
    }

    @Test
    @DisplayName("cookieSerializer bean is configured")
    void cookieSerializer_WhenCreated_ShouldNotBeNull() {
        assertThat(config.cookieSerializer()).isNotNull();
    }
}
