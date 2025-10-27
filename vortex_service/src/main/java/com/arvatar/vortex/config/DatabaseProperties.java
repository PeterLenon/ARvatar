package com.arvatar.vortex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.database")
public class DatabaseProperties {
    /**
     * Flag indicating whether the database writer should be enabled. Defaults to {@code false}
     * so the service can boot when no database credentials are supplied.
     */
    private boolean enabled;

    /** JDBC URL for the Postgres instance backing pgvector storage. */
    private String url;

    /** Database user name. */
    private String username;

    /** Database password. */
    private String password;

    /**
     * Whether the writer should attempt to create the schema on startup. Defaults to {@code true}
     * to retain the previous behaviour for development environments.
     */
    private boolean initializeSchema = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }
}
