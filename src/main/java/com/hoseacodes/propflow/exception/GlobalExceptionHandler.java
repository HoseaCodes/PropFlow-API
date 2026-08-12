package com.hoseacodes.propflow.exception;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Translates exceptions into RFC 7807 {@code application/problem+json}.
 *
 * <p>Replaces a handler that covered exactly one exception type, wrote to
 * {@code System.out}, and returned {@code ex.getMessage()} to the caller --
 * which for persistence failures included table, column, and constraint names.
 *
 * <h2>Why this extends {@link ResponseEntityExceptionHandler}</h2>
 * That base class already maps Spring MVC's own exceptions to their correct
 * statuses: unsupported method to 405, unsupported media type to 415, no
 * handler to 404. An earlier version of this class did not extend it and
 * declared a catch-all {@code @ExceptionHandler(Exception.class)}, which
 * swallowed all of them -- a POST to a GET-only path returned 500 instead of
 * 405, and was logged as an unhandled bug. A catch-all must be the last resort,
 * never the first responder; extending the framework handler keeps the precise
 * statuses and leaves the catch-all for genuine surprises.
 *
 * <h2>The split that matters</h2>
 * Full detail goes to the log, where operators can reach it. The client gets a
 * safe summary plus a correlation id it can quote in a support request, letting
 * an engineer find the exact log line without the response ever carrying a
 * stack trace, a SQL fragment, or an internal class name.
 *
 * <p>Unexpected exceptions are logged at ERROR with the stack trace. Expected
 * ones -- a 404, a validation failure -- are not logged at all: they are normal
 * API traffic, and logging them turns routine client mistakes into alert noise.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------
    // Domain exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleViolationException ex,
                                            HttpServletRequest request) {
        // 422, not 400: the payload parsed and its individual fields are
        // well-formed, but the combination violates a domain rule.
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation",
                ex.getMessage(), request.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Persistence and concurrency
    // ------------------------------------------------------------------

    /**
     * A database constraint rejected the write.
     *
     * <p>Reached when a check-then-act guard loses a race -- two concurrent
     * registrations for the same email, for instance. The service-layer check
     * produces a friendlier message in the common case; this is the backstop
     * that makes the invariant actually hold.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex,
                                             HttpServletRequest request) {
        String correlationId = newCorrelationId();
        // Logged in full because the constraint name is what an engineer needs;
        // never returned, because it exposes the schema.
        log.warn("Data integrity violation [{}]", correlationId, ex);

        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with existing data.", request.getRequestURI());
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    /** Two writers updated the same row concurrently; the loser is told to retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "This resource was modified by another request. Re-read it and try again.",
                request.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

    /**
     * Failed sign-in.
     *
     * <p>One message for every cause. Distinguishing "no such user" from "wrong
     * password" is an account-enumeration oracle, so the response body must be
     * byte-for-byte identical in both cases.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex,
                                              HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
                "Invalid username or password.", request.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Request binding
    // ------------------------------------------------------------------

    /** A path variable or query parameter of the wrong type, e.g. /properties/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '%s' has an invalid value.".formatted(ex.getName()),
                request.getRequestURI());
    }

    /**
     * Bean Validation failures on an {@code @Valid @RequestBody}.
     *
     * <p>Reports every invalid field at once rather than the first. A client
     * fixing a form should not have to submit five times to discover five
     * problems.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.merge(error.getField(), error.getDefaultMessage(),
                    (existing, added) -> existing + "; " + added);
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.put(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.", uriOf(request));
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** Malformed JSON, or a body that cannot be bound to the target type. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // ex.getMessage() is deliberately discarded: it can echo the offending
        // payload and internal type names back to the caller.
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be parsed as valid JSON.", uriOf(request));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Enriches every response produced by the framework's own handlers -- 405,
     * 415, 404 and friends -- so they carry the same {@code instance} and
     * {@code timestamp} fields as the responses built here. Clients should not
     * have to branch on which layer produced an error.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ResponseEntity<Object> response =
                super.handleExceptionInternal(ex, body, headers, statusCode, request);

        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (problem.getInstance() == null) {
                problem.setInstance(URI.create(uriOf(request)));
            }
            problem.setProperty("timestamp", Instant.now().toString());
        }
        return response;
    }

    // ------------------------------------------------------------------
    // Last resort
    // ------------------------------------------------------------------

    /**
     * Anything unanticipated.
     *
     * <p>An exception reaching here is a bug, so it is logged at ERROR with the
     * full stack trace and a correlation id. The response says nothing beyond
     * that id.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = newCorrelationId();
        log.error("Unhandled exception [{}] on {} {}",
                correlationId, request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Quote the correlation id when reporting this.",
                request.getRequestURI());
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    // ------------------------------------------------------------------

    private static ProblemDetail problem(HttpStatus status, String title, String detail,
                                         String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(instance));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    private static String uriOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
