package com.hoseacodes.propflow.exception;

/**
 * Thrown when a requested resource does not exist, or exists but is not
 * visible to the caller.
 *
 * <p>Maps to HTTP 404.
 *
 * <p>Deliberately also used for resources the caller is not permitted to see,
 * rather than 403. Returning 403 confirms that a resource with that identifier
 * exists, which lets an attacker enumerate valid IDs by probing. "Not found"
 * and "not yours" should be indistinguishable from outside.
 *
 * <p>Extends {@code RuntimeException} rather than a checked exception so that
 * it propagates cleanly out of service methods to the handler without every
 * intermediate signature declaring it. Unlike the bare
 * {@code RuntimeException("Property not found")} it replaces, it is a distinct
 * type, so a handler can catch it without also catching genuine bugs.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super("%s not found with id: %s".formatted(resourceType, id));
    }
}
