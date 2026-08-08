package com.hoseacodes.propflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * <p>No explicit {@code scanBasePackages}, {@code @EntityScan}, or
 * {@code @EnableJpaRepositories} is declared. {@code @SpringBootApplication}
 * already scans downward from this class's package, and Spring Boot's
 * auto-configuration discovers entities and repositories from the same root.
 * The previous explicit declarations were redundant and had drifted -- they
 * referenced a {@code property_management} package that does not exist, which
 * Spring silently tolerated.
 */
@SpringBootApplication
public class PropFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(PropFlowApplication.class, args);
	}

}
