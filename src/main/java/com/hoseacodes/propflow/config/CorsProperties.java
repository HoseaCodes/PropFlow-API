package com.hoseacodes.propflow.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allowed browser origins, bound from {@code propflow.cors.allowed-origins}.
 *
 * <p>Origins belong in configuration, not in code: they differ per
 * environment, and a hardcoded production URL forces a rebuild to change.
 *
 * <p>This replaces four conflicting CORS declarations -- a global
 * {@code WebMvcConfigurer} allowing one origin with credentials, plus three
 * separate {@code @CrossOrigin} annotations, one of which allowed every origin
 * on the authentication endpoints. Controller-level annotations override the
 * global registry, so the effective policy differed per endpoint with no single
 * place to read it.
 */
@ConfigurationProperties(prefix = "propflow.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);

        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "propflow.cors.allowed-origins must not contain \"*\". List origins explicitly.");
        }
    }
}
