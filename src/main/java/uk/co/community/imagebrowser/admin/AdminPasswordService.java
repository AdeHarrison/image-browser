package uk.co.community.imagebrowser.admin;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.repository.AdminConfigRepository;

/**
 * Stores the admin password as a one-way BCrypt hash in the {@code app_config}
 * table (key {@value #ADMIN_PASSWORD_HASH_KEY}).
 *
 * On first run, when no hash is stored yet, a default password is hashed and
 * persisted so the admin panel is reachable out of the box.
 */
@Service
public class AdminPasswordService {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordService.class);

    /** Minimum length enforced when setting a new admin password. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    public static final String ADMIN_PASSWORD_HASH_KEY = "admin_password_hash";

    /** BCrypt hash of the default password ("changeme"), used to seed app_config on first run. */
    private static final String DEFAULT_PASSWORD_HASH =
            "$2a$10$IIrVDAuaaw8LdH5fttpjg.XFj6XQU0Phfv/uJ5LYL5KNOJDr8VkDa";

    private final AdminConfigRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String passwordHash;

    public AdminPasswordService(AdminConfigRepository repository) {
        this.repository = repository;
    }

    // Calls repository.createSchemaIfNotExists() itself (idempotent) rather than relying on
    // DatabaseInitialiser, since @PostConstruct order between beans isn't guaranteed.
    @PostConstruct
    public void load() {
        repository.createSchemaIfNotExists();

        passwordHash = repository.getValue(ADMIN_PASSWORD_HASH_KEY)
                .orElseGet(() -> {
                    log.warn("No admin password set. Seeding default password — change it before deploying.");
                    repository.setValue(ADMIN_PASSWORD_HASH_KEY, DEFAULT_PASSWORD_HASH);
                    return DEFAULT_PASSWORD_HASH;
                });
    }

    public boolean verify(String candidate) {
        return candidate != null && encoder.matches(candidate, passwordHash);
    }

    /**
     * Hash and persist a new admin password.
     *
     * @return false if the password is shorter than {@link #MIN_PASSWORD_LENGTH}
     *         (and nothing is changed); true once the new hash is stored.
     */
    public boolean setPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        passwordHash = encoder.encode(newPassword);
        repository.setValue(ADMIN_PASSWORD_HASH_KEY, passwordHash);
        return true;
    }
}
