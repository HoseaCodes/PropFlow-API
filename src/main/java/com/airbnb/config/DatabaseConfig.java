// src/main/java/com/airbnb/config/DatabaseConfig.java
package com.airbnb.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    @Value("${JDBC_DATABASE_URL:jdbc:postgresql://localhost:5432/airbnb_management}")
    private String dbUrl;

    @Value("${JDBC_DATABASE_USERNAME:postgres}")
    private String dbUsername;

    @Value("${JDBC_DATABASE_PASSWORD:postgres}")
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Configure the connection pool
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        
        // Optional: Configure pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        
        return new HikariDataSource(config);
    }
}