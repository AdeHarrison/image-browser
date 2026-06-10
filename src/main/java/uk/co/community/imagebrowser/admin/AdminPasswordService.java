package uk.co.community.imagebrowser.admin;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import uk.co.community.imagebrowser.model.AppConfig;
import uk.co.community.imagebrowser.repository.ImageRepository;

/**
 * Stores the admin password as a one-way BCrypt hash in the {@code app_config}
 * table (key {@value AppConfig#ADMIN_PASSWORD_HASH_KEY}).
 *
 * On first run, when no hash is stored yet, a default password is hashed and
 * persisted so the admin panel is reachable out of the box.
 */
@Service
public class AdminPasswordService {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordService.class);

    /** BCrypt hash of the default password, used to seed app_config on first run. */
    private static final String DEFAULT_PASSWORD_HASH =
            "$2a$10$OhE9FeffFTAElNYejF2Mae1rbT8qBl2Il0CLcyi7oV0MmV31zUnaG";

    private final ImageRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String passwordHash;

    public AdminPasswordService(ImageRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void load() {
        repository.createSchema();

        passwordHash = repository.getConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY)
                .orElseGet(() -> {
                    log.warn("No admin password set. Seeding default password — change it before deploying.");
                    repository.setConfig(AppConfig.ADMIN_PASSWORD_HASH_KEY, DEFAULT_PASSWORD_HASH);
                    return DEFAULT_PASSWORD_HASH;
                });
    }

    public boolean verify(String candidate) {
        return candidate != null && encoder.matches(candidate, passwordHash);
    }
}
