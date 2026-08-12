package com.hoseacodes.propflow.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted to {@code POST /api/auth/signin}.
 */
public record SignInRequest(

        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password
) {
}
