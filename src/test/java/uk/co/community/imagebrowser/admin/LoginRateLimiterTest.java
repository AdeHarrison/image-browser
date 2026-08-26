package uk.co.community.imagebrowser.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginRateLimiter")
class LoginRateLimiterTest {

    private static final String IP = "203.0.113.7";

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter(3, 5);
    }

    @Test
    @DisplayName("an IP with no recorded attempts is not blocked")
    void isBlocked_WithNoAttempts_ShouldReturnFalse() {
        assertThat(limiter.isBlocked(IP)).isFalse();
    }

    @Test
    @DisplayName("an IP stays unblocked below the failure cap")
    void isBlocked_BelowCap_ShouldReturnFalse() {
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);

        assertThat(limiter.isBlocked(IP)).isFalse();
    }

    @Test
    @DisplayName("an IP is blocked once it reaches the failure cap")
    void isBlocked_AtCap_ShouldReturnTrue() {
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);

        assertThat(limiter.isBlocked(IP)).isTrue();
    }

    @Test
    @DisplayName("recordSuccess clears tracked failures so the IP is unblocked")
    void recordSuccess_AfterFailures_ShouldClearBlock() {
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);
        assertThat(limiter.isBlocked(IP)).isTrue();

        limiter.recordSuccess(IP);

        assertThat(limiter.isBlocked(IP)).isFalse();
    }

    @Test
    @DisplayName("failures for one IP do not affect another IP")
    void isBlocked_IsScopedPerIp() {
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);

        assertThat(limiter.isBlocked("198.51.100.9")).isFalse();
    }
}
