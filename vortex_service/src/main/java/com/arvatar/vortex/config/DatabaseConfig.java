package com.arvatar.vortex.config;

import com.arvatar.vortex.service.DatabaseWriter;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {

    @Bean
    public DataSource vortexDataSource(DatabaseProperties properties) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("vortex.database.url must be provided");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(properties.getUrl());
        dataSource.setUser(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }

    @Bean
    public DatabaseWriter databaseWriter(DataSource dataSource, DatabaseProperties properties) {
        return new DatabaseWriter(dataSource, properties.isInitializeSchema());
    }
}
