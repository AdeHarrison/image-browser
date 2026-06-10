package uk.co.community.imagebrowser.model;

/**
 * Key/value config entry stored in the app_config table.
 */
public record AppConfig(String key, String value) {
    public static final String LAST_UPDATED_KEY = "last_updated";
    public static final String ADMIN_PASSWORD_HASH_KEY = "admin_password_hash";
}
