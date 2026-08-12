package com.hoseacodes.propflow.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders authentication and authorization failures as RFC 7807
 * {@code application/problem+json}.
 *
 * <p>Security failures are handled by servlet filters, which sit outside the
 * {@code @RestControllerAdvice} exception handling that covers controllers.
 * Without this, Spring Security would fall back to a container error page, so a
 * client that receives clean JSON on every success would get an HTML body on
 * 401 -- the two most predictable failures in the entire API. Error responses
 * are part of the API contract.
 *
 * <p>Messages are deliberately generic. "Bad credentials" versus "no such user"
 * tells an attacker which usernames exist.
 */
@Component
public class SecurityProblemResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityProblemResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Invoked when an unauthenticated caller requests a protected resource. */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid bearer token is required to access this resource.");
    }

    /** Invoked when an authenticated caller lacks the required authority. */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN,
                "Access denied",
                "Your account is not permitted to perform this action.");
    }

    private void write(HttpServletRequest request,
                       HttpServletResponse response,
                       HttpStatus status,
                       String title,
                       String detail) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
