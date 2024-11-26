package com.airbnb.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    @Value("${RENDER_DATABASE_URL}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() throws URISyntaxException {
        URI dbUri = new URI(databaseUrl);
        
        String username = dbUri.getUserInfo().split(":")[0];
        String password = dbUri.getUserInfo().split(":")[1];
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        
        // Additional Hikari configuration
        config.addDataSourceProperty("socketTimeout", "30");
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        
        return new HikariDataSource(config);
    }

    @PostConstruct
    public void printDatabaseInfo() {
        try {
            URI dbUri = new URI(databaseUrl);
            System.out.println("Database Host: " + dbUri.getHost());
            System.out.println("Database Port: " + dbUri.getPort());
            System.out.println("Database Path: " + dbUri.getPath());
        } catch (URISyntaxException e) {
            System.err.println("Invalid database URL format: " + e.getMessage());
        }
    }
}