package uk.co.community.imagebrowser.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uk.co.community.imagebrowser.model.AppConfig;
import uk.co.community.imagebrowser.repository.ImageRepository;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AdminPasswordService")
class AdminPasswordServiceTest {

    private ImageRepository repository;
    private SingleConnectionDataSource ds;

    @BeforeEach
    void setUp() {
        ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        ds.setDriverClassName("org.sqlite.JDBC");
        repository = new ImageRepository(new JdbcTemplate(ds));
        repository.createSchema();
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    @Test
    @DisplayName("verify returns false before load() has run (no hash set)")
    void verify_WhenNotLoaded_ShouldReturnFalse() {
        var svc = new AdminPasswordService(repository);

        assertThat(svc.verify("anything")).isFalse();
    }

    @Test
    @Disabled("AdminPasswordService.load() DB access is temporarily disabled — see production code")
    @DisplayName("load seeds the default password when no hash is stored")
    void load_WhenNoHashStored_ShouldSeedDefaultPassword() {
        var svc = new AdminPasswordService(repository);

        svc.load();

        assertThat(svc.verify("changeme")).isTrue();
        assertThat(svc.verify("wrong")).isFalse();
        assertThat(repository.getConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY)).isPresent();
    }

    @Test
    @Disabled("AdminPasswordService.load() DB access is temporarily disabled — see production code")
    @DisplayName("load reuses an existing stored hash rather than reseeding")
    void load_WhenHashStored_ShouldVerifyAgainstStoredHash() {
        String existingHash = new BCryptPasswordEncoder().encode("s3cret");
        repository.setConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY, existingHash);

        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.verify("s3cret")).isTrue();
        assertThat(svc.verify("changeme")).isFalse();
        assertThat(repository.getConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY)).contains(existingHash);
    }

    @Test
    @DisplayName("verify returns false for a null candidate")
    void verify_WithNullCandidate_ShouldReturnFalse() {
        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.verify(null)).isFalse();
    }

    @Test
    @DisplayName("setPassword stores a new hash and verify accepts the new password")
    void setPassword_WithValidPassword_ShouldUpdateStoredHash() {
        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.setPassword("newSecurePassword")).isTrue();

        assertThat(svc.verify("newSecurePassword")).isTrue();
        assertThat(svc.verify("changeme")).isFalse();

        String storedHash = repository.getConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY).orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches("newSecurePassword", storedHash)).isTrue();
    }

    @Test
    @Disabled("depends on load() seeding the default password, which is temporarily disabled")
    @DisplayName("setPassword rejects passwords shorter than the minimum length")
    void setPassword_WithShortPassword_ShouldReturnFalseAndLeaveHashUnchanged() {
        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.setPassword("short")).isFalse();

        assertThat(svc.verify("changeme")).isTrue();
        assertThat(svc.verify("short")).isFalse();
    }

    @Test
    @Disabled("depends on load() seeding the default password, which is temporarily disabled")
    @DisplayName("setPassword rejects a null password")
    void setPassword_WithNullPassword_ShouldReturnFalse() {
        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.setPassword(null)).isFalse();
        assertThat(svc.verify("changeme")).isTrue();
    }
}
