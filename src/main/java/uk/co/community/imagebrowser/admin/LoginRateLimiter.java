package uk.co.community.imagebrowser.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window brute-force guard for {@code POST /admin/login}, keyed by client IP.
 * Bcrypt already slows each individual guess; this bounds how many guesses a single
 * source can make in a window regardless of request rate.
 */
@Component
public class LoginRateLimiter {

    private record Attempts(int count, Instant windowStart) {}

    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimiter(
            @Value("${app.admin.login.max-attempts:5}") int maxAttempts,
            @Value("${app.admin.login.window-minutes:5}") int windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** True if this IP has hit the failure cap within the current window. */
    public boolean isBlocked(String ip) {
        Attempts attempts = attemptsByIp.get(ip);
        if (attempts == null || windowExpired(attempts)) {
            return false;
        }
        return attempts.count() >= maxAttempts;
    }

    public void recordFailure(String ip) {
        attemptsByIp.compute(ip, (key, existing) -> {
            if (existing == null || windowExpired(existing)) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(existing.count() + 1, existing.windowStart());
        });
    }

    /** Clears any tracked failures for this IP, e.g. on a successful login. */
    public void recordSuccess(String ip) {
        attemptsByIp.remove(ip);
    }

    private boolean windowExpired(Attempts attempts) {
        return Instant.now().isAfter(attempts.windowStart().plus(window));
    }
}
