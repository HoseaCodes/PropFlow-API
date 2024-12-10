package com.airbnb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = { "com.airbnb", "com.airbnb.property_management" })
@EnableJpaRepositories(basePackages = "com.airbnb.repository")
@EntityScan(basePackages = "com.airbnb.model")
public class AirbnbPropertyManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(AirbnbPropertyManagementApplication.class, args);
	}

}
