package com.hoseacodes.propflow.dto.response;

/**
 * Issued on successful authentication.
 *
 * @param accessToken the signed JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType   always {@code Bearer}; included so clients do not hardcode it
 * @param expiresIn   token lifetime in seconds, so a client can refresh before
 *                    expiry rather than discovering it through a 401
 * @param user        the authenticated account, without credentials
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
