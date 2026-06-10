package uk.co.community.imagebrowser.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    @DisplayName("load seeds the default password when no hash is stored")
    void load_WhenNoHashStored_ShouldSeedDefaultPassword() {
        var svc = new AdminPasswordService(repository);

        svc.load();

        assertThat(svc.verify("BoSPhotoViewer")).isTrue();
        assertThat(svc.verify("wrong")).isFalse();
        assertThat(repository.getConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY)).isPresent();
    }

    @Test
    @DisplayName("load reuses an existing stored hash rather than reseeding")
    void load_WhenHashStored_ShouldVerifyAgainstStoredHash() {
        String existingHash = new BCryptPasswordEncoder().encode("s3cret");
        repository.setConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY, existingHash);

        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.verify("s3cret")).isTrue();
        assertThat(svc.verify("BoSPhotoViewer")).isFalse();
        assertThat(repository.getConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY)).contains(existingHash);
    }

    @Test
    @DisplayName("verify returns false for a null candidate")
    void verify_WithNullCandidate_ShouldReturnFalse() {
        var svc = new AdminPasswordService(repository);
        svc.load();

        assertThat(svc.verify(null)).isFalse();
    }
}
