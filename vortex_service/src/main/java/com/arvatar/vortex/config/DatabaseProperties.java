package com.arvatar.vortex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.database")
public class DatabaseProperties {

    /** JDBC URL for the Postgres instance backing pgvector storage. */
    private String url;

    /** Database user name. */
    private String username;

    /** Database password. */
    private String password;

    /**
     * Whether the writer should attempt to create the schema on startup. Defaults to {@code true}
     * to retain the previous behavior for development environments.
     */
    private boolean initializeSchema = true;

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
