package com.hoseacodes.propflow.exception;

/**
 * Thrown when a request is syntactically valid but violates a domain rule.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity. The distinction from 400 is worth
 * keeping: 400 means "I could not understand this request", while 422 means "I
 * understood it and it is not allowed". Recording an INCOME transaction against
 * the MORTGAGE category is the latter -- every field is individually valid, but
 * the combination is not.
 *
 * <p>Rules of this kind cannot be expressed as field annotations because they
 * span fields, so they live in the service layer where the domain does.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
