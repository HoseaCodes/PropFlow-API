package com.hoseacodes.propflow.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hoseacodes.propflow.security.JwtAuthenticationFilter;
import com.hoseacodes.propflow.security.JwtProperties;
import com.hoseacodes.propflow.security.SecurityProblemResponder;

/**
 * Security configuration for a stateless, token-authenticated API.
 *
 * <p>Replaces a configuration that called {@code requestMatchers("/api/**")
 * .permitAll()} while every controller in the application was mounted under
 * {@code /api} -- so the entire API, including full CRUD over user accounts and
 * financial records, was reachable anonymously.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    /**
     * Paths that must remain reachable without a token.
     *
     * <p>Health probes are here because a load balancer or orchestrator cannot
     * hold a credential. They return only UP or DOWN to an anonymous caller --
     * component detail requires ADMIN, configured in application.properties.
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/signin",
            "/api/auth/signup",
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProblemResponder problemResponder;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityProblemResponder problemResponder,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.problemResponder = problemResponder;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS is configured here rather than through WebMvcConfigurer.
                // With Spring Security on the chain, preflight OPTIONS requests
                // must be handled before authorization runs, or the browser's
                // preflight is rejected with a 401 and the real request is
                // never sent.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF protection defends against a browser automatically
                // attaching ambient credentials -- cookies -- to a
                // cross-site request. This API authenticates with an
                // Authorization header that a browser never attaches on its
                // own, and holds no session, so there is no ambient credential
                // to abuse. Disabling it here is a reasoned conclusion from
                // being stateless, not a convenience. If cookie-based auth is
                // ever added, this must be re-enabled.
                .csrf(AbstractHttpConfigurer::disable)

                // No HttpSession is created or consulted. Any instance can
                // serve any request, which is what makes horizontal scaling
                // work without sticky sessions or a shared session store.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Preflight requests carry no credentials by design.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(PUBLIC_PATHS).permitAll()

                        // Everything else under /actuator -- notably the
                        // Prometheus scrape endpoint, which exposes request
                        // paths, timings, and JVM internals. In a real
                        // deployment this would additionally be bound to an
                        // internal-only port or network rather than relying on
                        // authentication alone.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Ordering matters: the first match wins, so the
                        // self-service route must precede the admin rule that
                        // would otherwise swallow it.
                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // Default deny. Anything not named above requires
                        // authentication, so a new controller is protected
                        // unless someone deliberately opens it -- the opposite
                        // of the previous configuration, where a new endpoint
                        // under /api was public by default.
                        .anyRequest().authenticated())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemResponder)
                        .accessDeniedHandler(problemResponder))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location"));

        // Credentials are not allowed. In CORS terms "credentials" means
        // cookies and TLS client certificates, which this API does not use --
        // the bearer token travels in an explicit Authorization header that the
        // browser never attaches automatically. Leaving this false keeps the
        // policy honest and avoids the wildcard-origin restriction that
        // allowCredentials imposes.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * BCrypt with the default strength of 10.
     *
     * <p>BCrypt is deliberately slow and salts each hash individually, so a
     * stolen hash cannot be attacked with precomputed rainbow tables and must
     * be brute-forced one password at a time. Strength is a cost factor: each
     * increment doubles the work for both the attacker and this application.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Compare against a dummy hash when the user does not exist, so that a
        // request for an unknown username takes the same time as one for a
        // known username with a wrong password. Without this, response timing
        // reveals which usernames are registered.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
