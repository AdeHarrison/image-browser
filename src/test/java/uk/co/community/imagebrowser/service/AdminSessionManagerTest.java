package uk.co.community.imagebrowser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.co.community.imagebrowser.admin.AdminSessionManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AdminSessionManager")
class AdminSessionManagerTest {

    private AdminSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new AdminSessionManager();
    }

    @Test
    @DisplayName("first login succeeds when no session is active")
    void login_WhenNoSessionActive_ShouldSucceed() {
        assertThat(manager.login("session-1")).isTrue();
    }

    @Test
    @DisplayName("second login fails when a session is already active")
    void login_WhenSessionAlreadyActive_ShouldFail() {
        manager.login("session-1");
        assertThat(manager.login("session-2")).isFalse();
    }

    @Test
    @DisplayName("isAdminLoggedIn returns false initially")
    void isAdminLoggedIn_WhenNoLogin_ShouldReturnFalse() {
        assertThat(manager.isAdminLoggedIn()).isFalse();
    }

    @Test
    @DisplayName("isAdminLoggedIn returns true after login")
    void isAdminLoggedIn_WhenLoggedIn_ShouldReturnTrue() {
        manager.login("session-1");
        assertThat(manager.isAdminLoggedIn()).isTrue();
    }

    @Test
    @DisplayName("isAdminLoggedIn returns false after logout")
    void isAdminLoggedIn_WhenLoggedOut_ShouldReturnFalse() {
        manager.login("session-1");
        manager.logout("session-1");
        assertThat(manager.isAdminLoggedIn()).isFalse();
    }

    @Test
    @DisplayName("logout only clears the active session — other sessions cannot log out admin")
    void logout_WhenWrongSession_ShouldNotClearActiveSession() {
        manager.login("session-1");
        manager.logout("session-99"); // different session — should have no effect
        assertThat(manager.isAdminLoggedIn()).isTrue();
    }

    @Test
    @DisplayName("isActiveSession returns true for the logged-in session")
    void isActiveSession_WhenSessionMatches_ShouldReturnTrue() {
        manager.login("session-1");
        assertThat(manager.isActiveSession("session-1")).isTrue();
    }

    @Test
    @DisplayName("isActiveSession returns false for a different session")
    void isActiveSession_WhenSessionDiffers_ShouldReturnFalse() {
        manager.login("session-1");
        assertThat(manager.isActiveSession("session-2")).isFalse();
    }

    @Test
    @DisplayName("isActiveSession returns false for null")
    void isActiveSession_WhenSessionNull_ShouldReturnFalse() {
        manager.login("session-1");
        assertThat(manager.isActiveSession(null)).isFalse();
    }

    @Test
    @DisplayName("login can succeed again after logout")
    void login_WhenAfterLogout_ShouldSucceedAgain() {
        manager.login("session-1");
        manager.logout("session-1");
        assertThat(manager.login("session-2")).isTrue();
    }

    @Test
    @DisplayName("forceLogout releases the lock")
    void forceLogout_WhenInvoked_ShouldReleaseLock() {
        manager.login("session-1");
        manager.forceLogout("session-1");
        assertThat(manager.isAdminLoggedIn()).isFalse();
    }

    @Test
    @DisplayName("concurrent login attempts — only one wins")
    void login_WhenConcurrentAttempts_ShouldOnlyAllowOneToWin() throws InterruptedException {
        int            threads     = 20;
        var            latch       = new CountDownLatch(1);
        var            successCount = new AtomicInteger(0);
        var            executor    = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final String sessionId = "session-" + i;
            executor.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (manager.login(sessionId)) {
                    successCount.incrementAndGet();
                }
            });
        }

        latch.countDown(); // release all threads simultaneously
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successCount.get())
                .as("Exactly one concurrent login should succeed")
                .isEqualTo(1);
    }
}
