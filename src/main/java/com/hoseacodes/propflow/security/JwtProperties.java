package com.hoseacodes.propflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing configuration, bound from {@code propflow.jwt.*}.
 *
 * <p>There is deliberately no default secret. A hardcoded or fallback signing
 * key is a forgery oracle: anyone who has read the source can mint a token for
 * any user, and the resulting token is indistinguishable from a legitimate one.
 * Failing to start is the correct behaviour when the key is absent -- an
 * application that boots with a known key is worse than one that does not boot.
 *
 * @param secret     HMAC signing key, supplied via the {@code JWT_SECRET}
 *                   environment variable
 * @param expiration how long an issued token remains valid
 */
@ConfigurationProperties(prefix = "propflow.jwt")
public record JwtProperties(String secret, Duration expiration) {

    /** HS256 requires a key of at least 256 bits; RFC 7518 mandates it. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    propflow.jwt.secret is not configured.

                    Set the JWT_SECRET environment variable. Generate one with:
                        openssl rand -base64 48

                    The application will not start without it: a default signing \
                    key would let anyone holding the source forge tokens.""");
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "propflow.jwt.secret must be at least %d bytes for HS256 (got %d). Generate one with: openssl rand -base64 48"
                            .formatted(MINIMUM_SECRET_BYTES, length));
        }

        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("propflow.jwt.expiration must be a positive duration");
        }
    }
}
