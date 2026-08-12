package com.hoseacodes.propflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload for {@code POST /api/auth/signup}.
 *
 * <p>This deliberately does not accept {@code id}, {@code role}, or
 * {@code version}. The previous endpoint bound the {@code User} entity
 * directly, so a caller could submit any field the entity exposed -- including
 * one that would have granted themselves an administrative role once roles
 * existed. Restricting the accepted shape is what prevents mass assignment;
 * stripping fields after binding is not.
 */
public record SignUpRequest(

        @NotBlank(message = "email is required")
        @Email(message = "must be a well-formed email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
        String username,

        // Length is the only property constraint applied here. A minimum of 8
        // is a floor, not a policy; NIST guidance favours length over
        // composition rules, and BCrypt silently truncates beyond 72 bytes.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

        @Size(max = 255, message = "firstName must be at most 255 characters")
        String firstName,

        @Size(max = 255, message = "lastName must be at most 255 characters")
        String lastName
) {
}
