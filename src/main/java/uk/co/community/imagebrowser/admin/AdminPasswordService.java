package uk.co.community.imagebrowser.admin;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads the admin password from an external properties file.
 * This keeps the password out of the JAR and allows non-technical
 * volunteers to change it by editing a text file.
 *
 * File format (admin.properties):
 *   admin.password=yourpassword
 */
@Service
public class AdminPasswordService {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordService.class);

    private final Path   propertiesPath;
    private       String password;

    public AdminPasswordService(
            @Value("${app.admin.properties:admin.properties}") String propertiesPath) {
        this.propertiesPath = Path.of(propertiesPath);
    }

    @PostConstruct
    public void load() {
        if (!Files.exists(propertiesPath)) {
            log.warn("Admin properties file not found at '{}'. Using default password. " +
                     "Create the file to set a secure password.", propertiesPath);
            password = "changeme";
            return;
        }
        try {
            var props = new Properties();
            props.load(Files.newInputStream(propertiesPath));
            password = props.getProperty("admin.password", "changeme");
            log.info("Admin password loaded from {}", propertiesPath);
        } catch (IOException e) {
            log.error("Failed to load admin properties: {}", e.getMessage());
            password = "changeme";
        }
    }

    public boolean verify(String candidate) {
        return password != null && password.equals(candidate);
    }
}
