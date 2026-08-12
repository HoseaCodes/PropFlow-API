package com.hoseacodes.propflow.dto.response;

import com.hoseacodes.propflow.model.Role;
import com.hoseacodes.propflow.model.User;

/**
 * The public representation of a user.
 *
 * <p>The omission of {@code password} is the point of this type. The
 * {@code User} entity implements {@code UserDetails}, which requires a public
 * {@code getPassword()}, so returning the entity published every account's
 * BCrypt hash -- unauthenticated, via {@code GET /api/users}. Exposed hashes
 * enable offline brute force with no rate limiting and no detection.
 */
public record UserResponse(
        Long id,
        String email,
        String username,
        String firstName,
        String lastName,
        Role role
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole());
    }
}
