package com.airbnb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.airbnb", "com.airbnb.property_management"})
public class AirbnbPropertyManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(AirbnbPropertyManagementApplication.class, args);
	}

}
