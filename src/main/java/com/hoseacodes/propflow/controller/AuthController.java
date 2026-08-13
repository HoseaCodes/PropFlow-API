package com.hoseacodes.propflow.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoseacodes.propflow.dto.request.SignInRequest;
import com.hoseacodes.propflow.dto.request.SignUpRequest;
import com.hoseacodes.propflow.dto.response.AuthResponse;
import com.hoseacodes.propflow.dto.response.UserResponse;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.security.JwtService;
import com.hoseacodes.propflow.service.UserService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

/**
 * Registration and sign-in. The only endpoints reachable without a token.
 */
@RestController
@RequestMapping("/api/auth")
// The OpenAPI document applies a bearer requirement globally, matching the
// security chain's deny-by-default posture. These two endpoints are the
// exception, so the requirement is cleared here -- otherwise Swagger UI would
// send an Authorization header to the endpoint that issues the token.
@SecurityRequirements
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * Registers an account.
     *
     * <p>Returns 201 with a {@code Location} header, and a body containing no
     * credentials. The previous implementation returned the saved {@code User}
     * entity, which serialised the BCrypt hash straight back to the caller.
     *
     * <p>No token is issued here. Registration and authentication are separate
     * concerns, and keeping them separate means the sign-in path -- the one that
     * has to resist credential stuffing -- has exactly one implementation.
     */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        User created = userService.register(request);
        UserResponse body = UserResponse.from(created);

        return ResponseEntity
                .created(URI.create("/api/users/" + created.getId()))
                .body(body);
    }

    /**
     * Exchanges credentials for a bearer token.
     *
     * <p>{@code AuthenticationManager.authenticate} delegates to
     * {@code DaoAuthenticationProvider}, which loads the user and compares the
     * submitted password against the stored BCrypt hash using the encoder's
     * constant-time matcher. On failure it throws {@code AuthenticationException},
     * which the exception handler maps to a 401 carrying no detail about
     * <em>why</em> -- distinguishing "no such user" from "wrong password" would
     * let an attacker enumerate accounts.
     *
     * <p>Note what is deliberately absent: the previous implementation stored
     * the result in {@code SecurityContextHolder}, which is thread-local and
     * cleared at the end of the request, and returned a plain string. The client
     * received nothing it could present on a subsequent call. The token below is
     * that missing piece.
     */
    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signIn(@Valid @RequestBody SignInRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = (User) authentication.getPrincipal();
        String token = jwtService.generateToken(user);

        return ResponseEntity.ok(AuthResponse.bearer(
                token, jwtService.getExpirationSeconds(), UserResponse.from(user)));
    }
}
