package uk.co.community.imagebrowser.admin;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Enforces a single active admin session at a time.
 * Thread-safe via AtomicReference compareAndSet.
 */
@Component
public class AdminSessionManager {

    private final AtomicReference<String> activeSessionId = new AtomicReference<>(null);

    /**
     * Attempt to claim the admin session.
     * @return true if this session is now the active admin; false if another session holds it.
     */
    public boolean login(String sessionId) {
        return activeSessionId.compareAndSet(null, sessionId);
    }

    /**
     * Release the admin session. Only succeeds if the given sessionId is the active one.
     */
    public void logout(String sessionId) {
        activeSessionId.compareAndSet(sessionId, null);
    }

    /**
     * True if there is currently an active admin session (any session).
     */
    public boolean isAdminLoggedIn() {
        return activeSessionId.get() != null;
    }

    /**
     * True if the given sessionId is the current active admin session.
     */
    public boolean isActiveSession(String sessionId) {
        return sessionId != null && sessionId.equals(activeSessionId.get());
    }

    /**
     * Force-clear the lock — used when a session expires via HttpSessionListener.
     */
    public void forceLogout(String sessionId) {
        logout(sessionId);
    }
}
