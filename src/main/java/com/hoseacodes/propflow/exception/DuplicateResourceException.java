package com.hoseacodes.propflow.exception;

/**
 * Thrown when creating a resource would violate a uniqueness rule.
 *
 * <p>Maps to HTTP 409 Conflict, which is the accurate status: the request is
 * well-formed and the caller is permitted, but it conflicts with the current
 * state of the server. A 400 would wrongly suggest the payload is malformed.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
