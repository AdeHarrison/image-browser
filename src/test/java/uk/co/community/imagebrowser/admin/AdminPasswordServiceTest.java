package uk.co.community.imagebrowser.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uk.co.community.imagebrowser.repository.AdminConfigRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPasswordService")
class AdminPasswordServiceTest {

    @Mock private AdminConfigRepository repository;

    private AdminPasswordService service;

    @BeforeEach
    void setUp() {
        service = new AdminPasswordService(repository);
    }

    @Test
    @DisplayName("load seeds the default password when no hash is stored")
    void load_WhenNoHashStored_ShouldSeedDefaultPassword() {
        when(repository.getValue(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY)).thenReturn(Optional.empty());

        service.load();

        assertThat(service.verify("changeme")).isTrue();
        assertThat(service.verify("wrong")).isFalse();
        verify(repository).createSchemaIfNotExists();
        verify(repository).setValue(eq(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY), anyString());
    }

    @Test
    @DisplayName("load reuses an existing stored hash rather than reseeding")
    void load_WhenHashStored_ShouldVerifyAgainstStoredHash() {
        String existingHash = new BCryptPasswordEncoder().encode("s3cret");
        when(repository.getValue(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY)).thenReturn(Optional.of(existingHash));

        service.load();

        assertThat(service.verify("s3cret")).isTrue();
        assertThat(service.verify("changeme")).isFalse();
        verify(repository, never()).setValue(anyString(), anyString());
    }

    @Test
    @DisplayName("verify returns false before load() has run (no hash set)")
    void verify_WhenNotLoaded_ShouldReturnFalse() {
        assertThat(service.verify("anything")).isFalse();
    }

    @Test
    @DisplayName("verify returns false for a null candidate")
    void verify_WithNullCandidate_ShouldReturnFalse() {
        when(repository.getValue(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY)).thenReturn(Optional.empty());
        service.load();

        assertThat(service.verify(null)).isFalse();
    }

//    @Test
//    @DisplayName("setPassword stores a new hash and verify accepts the new password")
//    void setPassword_WithValidPassword_ShouldUpdateStoredHash() {
//        when(repository.getValue(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY)).thenReturn(Optional.empty());
//        service.load();
//
//        assertThat(service.setPassword("newSecurePassword")).isTrue();
//
//        assertThat(service.verify("newSecurePassword")).isTrue();
//        assertThat(service.verify("changeme")).isFalse();
//        verify(repository).setValue(eq(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY), anyString());
//    }

    @Test
    @DisplayName("setPassword rejects passwords shorter than the minimum length")
    void setPassword_WithShortPassword_ShouldReturnFalseAndLeaveHashUnchanged() {
        when(repository.getValue(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY)).thenReturn(Optional.empty());
        service.load();

        assertThat(service.setPassword("short")).isFalse();

        assertThat(service.verify("changeme")).isTrue();
        assertThat(service.verify("short")).isFalse();
    }

    @Test
    @DisplayName("setPassword rejects a null password")
    void setPassword_WithNullPassword_ShouldReturnFalse() {
        when(repository.getValue(AdminPasswordService.ADMIN_PASSWORD_HASH_KEY)).thenReturn(Optional.empty());
        service.load();

        assertThat(service.setPassword(null)).isFalse();
        assertThat(service.verify("changeme")).isTrue();
    }
}
