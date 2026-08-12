package com.hoseacodes.propflow.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}.
 *
 * <p>Registered before {@code UsernamePasswordAuthenticationFilter} so the
 * security context is populated by the time authorization rules are evaluated.
 *
 * <p>Extends {@link OncePerRequestFilter} because a servlet filter can
 * otherwise run again on forwards and error dispatches, which would repeat the
 * database lookup and, worse, re-run authentication against a request that has
 * already been decided.
 *
 * <h2>This filter never rejects a request</h2>
 * On a missing, malformed, or expired token it simply leaves the security
 * context empty and continues the chain. Spring Security's authorization rules
 * then decide: a protected endpoint produces 401 via the configured entry
 * point, and a public one still works. Rejecting here would break public
 * endpoints for any client that sends a stale token, and would duplicate a
 * decision that belongs in one place.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        // Skip when there is no token, or when something earlier in the chain
        // has already authenticated the request.
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        jwtService.extractUsername(token).ifPresent(username -> authenticate(username, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(String username, HttpServletRequest request) {
        try {
            // Reloaded from the database rather than trusted from the token's
            // claims. This costs one query per authenticated request and buys
            // the property that a deleted or modified account takes effect
            // immediately, instead of when the token happens to expire.
            UserDetails user = userDetailsService.loadUserByUsername(username);

            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {
            // A validly signed token for an account that no longer exists.
            // Leave the context unauthenticated; the request is rejected
            // downstream by the authorization rules.
            logger.debug("Token subject no longer resolves to a user");
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
