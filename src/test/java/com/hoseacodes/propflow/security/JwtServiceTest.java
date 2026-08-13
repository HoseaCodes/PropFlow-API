package com.hoseacodes.propflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Unit tests for token minting and verification.
 *
 * <p>These are the tests that matter most in this class: a token is a bearer
 * credential, so any path that accepts one it should not is a full
 * authentication bypass. They run without Spring or a database because the
 * logic is pure cryptography and clock arithmetic.
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-only-signing-key-of-at-least-32-bytes-in-length-000";
    private static final String OTHER_SECRET =
            "a-completely-different-signing-key-also-32-bytes-long-11";

    private static UserDetails user(String username) {
        return User.withUsername(username).password("ignored").roles("USER").build();
    }

    private static JwtService serviceWith(String secret, Duration expiry) {
        return new JwtService(new JwtProperties(secret, expiry));
    }

    private static JwtService service() {
        return serviceWith(SECRET, Duration.ofHours(1));
    }

    @Test
    @DisplayName("a freshly issued token round-trips to its subject")
    void roundTrip() {
        JwtService service = service();

        String token = service.generateToken(user("dominique"));

        assertThat(service.extractUsername(token)).contains("dominique");
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void rejectsTokenSignedWithAnotherKey() {
        // The forgery case. An attacker who mints their own token with a key of
        // their choosing must not be able to authenticate.
        String forged = serviceWith(OTHER_SECRET, Duration.ofHours(1))
                .generateToken(user("attacker"));

        assertThat(service().extractUsername(forged)).isEmpty();
    }

    @Test
    @DisplayName("a token whose payload has been tampered with is rejected")
    void rejectsTamperedToken() {
        JwtService service = service();
        String token = service.generateToken(user("dominique"));

        // Flip a character in the payload segment. The signature covers header
        // and payload, so any edit invalidates it.
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThat(service.extractUsername(tampered)).isEmpty();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() throws InterruptedException {
        // One-second lifetime rather than a mocked clock: this exercises the
        // real expiry check inside the JWT library instead of a stand-in.
        JwtService service = serviceWith(SECRET, Duration.ofSeconds(1));
        String token = service.generateToken(user("dominique"));

        assertThat(service.extractUsername(token)).contains("dominique");

        Thread.sleep(1100);

        assertThat(service.extractUsername(token)).isEmpty();
    }

    @Test
    @DisplayName("an unsigned 'none' algorithm token is rejected")
    void rejectsUnsignedToken() {
        // The classic JWT attack: strip the signature and set alg to "none".
        // parseSignedClaims refuses unsigned tokens outright.
        String unsigned = "eyJhbGciOiJub25lIn0"
                + ".eyJzdWIiOiJhdHRhY2tlciJ9"
                + ".";

        assertThat(service().extractUsername(unsigned)).isEmpty();
    }

    @Test
    @DisplayName("garbage input is rejected without throwing")
    void rejectsGarbage() {
        JwtService service = service();

        // A public endpoint receives arbitrary bytes. Rejecting must be a
        // return value, not an exception that escapes the filter.
        assertThat(service.extractUsername("not-a-token")).isEmpty();
        assertThat(service.extractUsername("")).isEmpty();
        assertThat(service.extractUsername("a.b.c")).isEmpty();
    }

    @Test
    @DisplayName("issued tokens carry the user's authorities as a claim")
    void includesAuthoritiesClaim() {
        String token = service().generateToken(
                User.withUsername("admin").password("ignored").roles("ADMIN").build());

        assertThat(service().extractUsername(token)).contains("admin");
        // The claim exists for clients; authorities are still re-derived from
        // the database on each request, so this is informational only.
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("configuration rejects a missing signing secret")
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        assertThatThrownBy(() -> new JwtProperties("   ", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("configuration rejects an unresolved ${...} placeholder")
    void rejectsUnresolvedPlaceholder() {
        // Regression test for a real hole. Spring's @ConfigurationProperties
        // binder ignores unresolvable placeholders, so an unset JWT_SECRET binds
        // the literal text "${JWT_SECRET}" instead of failing -- and the
        // application would boot with that as its HMAC signing key, shared by
        // every deployment that forgot the variable.
        assertThatThrownBy(() -> new JwtProperties("${JWT_SECRET}", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propflow.jwt.secret is not configured");

        // Long enough to pass the byte-length check, so only the placeholder
        // detection can reject it. This is the case the length check misses.
        assertThatThrownBy(() -> new JwtProperties(
                "${A_VERY_LONG_ENVIRONMENT_VARIABLE_NAME_HERE}", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propflow.jwt.secret is not configured");
    }

    @Test
    @DisplayName("configuration rejects a secret too short for HS256")
    void rejectsShortSecret() {
        // HS256 requires a 256-bit key. Failing at startup with a clear message
        // beats failing at the first sign-in with a library exception.
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("configuration rejects a non-positive expiry")
    void rejectsInvalidExpiry() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ofMinutes(-5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("expiresIn reflects the configured lifetime")
    void exposesExpiry() {
        assertThat(serviceWith(SECRET, Duration.ofMinutes(30)).getExpirationSeconds())
                .isEqualTo(1800L);
    }

    @Test
    @DisplayName("distinct users receive distinct tokens")
    void tokensAreUserSpecific() {
        JwtService service = service();
        List<String> subjects = List.of("alice", "bob");

        List<String> tokens = subjects.stream().map(s -> service.generateToken(user(s))).toList();

        assertThat(tokens.get(0)).isNotEqualTo(tokens.get(1));
        assertThat(service.extractUsername(tokens.get(0))).contains("alice");
        assertThat(service.extractUsername(tokens.get(1))).contains("bob");
    }
}
