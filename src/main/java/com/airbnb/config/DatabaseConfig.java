// src/main/java/com/airbnb/config/DatabaseConfig.java
package com.airbnb.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Value("${POSTGRES_URL}")
    private String dbUrl;

    @Value("${POSTGRES_USER}")
    private String dbUsername;

    @Value("${POSTGRES_PASSWORD}")
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Add "jdbc:" prefix if not present
        String jdbcUrl = dbUrl.startsWith("jdbc:") ? dbUrl : "jdbc:" + dbUrl;
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        
        // Connection pool settings
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        
        // Add these properties for better PostgreSQL handling
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
}