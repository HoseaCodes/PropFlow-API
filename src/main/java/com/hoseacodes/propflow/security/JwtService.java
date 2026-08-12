package com.hoseacodes.propflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Mints and validates the signed JWTs used for stateless authentication.
 *
 * <h2>Why JWT here</h2>
 * The token carries its own proof of authenticity: the server verifies the
 * signature with a key only it holds, so no session store lookup is needed and
 * any instance can serve any request. That is the property that makes the API
 * horizontally scalable without sticky sessions or shared session state.
 *
 * <h2>The tradeoff, stated plainly</h2>
 * A stateless token cannot be revoked before it expires. Deleting a user or
 * changing their password does not invalidate tokens already issued to them.
 * The mitigations are a short lifetime, a refresh-token flow, or a revocation
 * denylist -- and a denylist reintroduces exactly the server-side state that
 * motivated JWT in the first place. This project takes the short-lifetime
 * option and documents the limitation rather than pretending it away.
 *
 * <p>Partial mitigation: {@code JwtAuthenticationFilter} reloads the user from
 * the database on every request, so an account that has been deleted stops
 * authenticating immediately even though its token is still cryptographically
 * valid. That costs one query per request, which is a deliberate trade of some
 * statelessness for a meaningful safety property.
 *
 * <h2>Logging</h2>
 * Token values are never logged, at any level. A token in a log file is a
 * usable credential for anyone who can read that file.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expiration().toSeconds();
    }

    /**
     * Issues a token whose subject is the username.
     *
     * <p>The role is included as a claim for the benefit of clients rendering
     * UI, but it is never trusted for authorization: authorities are re-derived
     * from the database on each request. Trusting a claim for access control
     * would mean a role revoked in the database stays effective until the token
     * expires.
     */
    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("authorities", user.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Parses and cryptographically verifies a token, returning its subject.
     *
     * <p>Returns empty rather than throwing for any invalid token -- bad
     * signature, expired, malformed, or unsupported. Parsing untrusted input is
     * an expected condition on a public endpoint, not an exceptional one, and a
     * caller should not have to distinguish the failure modes to reject the
     * request.
     *
     * <p>{@code parseSignedClaims} verifies the signature <em>and</em> the
     * expiry. Reading claims without verifying the signature would accept any
     * token a client chose to fabricate.
     */
    public Optional<String> extractUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject()).filter(s -> !s.isBlank());
        } catch (JwtException | IllegalArgumentException ex) {
            // Log the reason, never the token.
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
