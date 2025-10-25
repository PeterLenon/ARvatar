package com.arvatar.vortex.config;

import com.arvatar.vortex.service.DatabaseWriter;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {

    @Bean
    @ConditionalOnProperty(prefix = "vortex.database", name = "enabled", havingValue = "true")
    public DataSource vortexDataSource(DatabaseProperties properties) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("vortex.database.url must be provided when database persistence is enabled");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(properties.getUrl());
        dataSource.setUser(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public DatabaseWriter databaseWriter(DataSource dataSource, DatabaseProperties properties) {
        return new DatabaseWriter(dataSource, properties.isInitializeSchema());
    }
}
