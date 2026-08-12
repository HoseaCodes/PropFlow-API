package com.hoseacodes.propflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoseacodes.propflow.dto.response.UserResponse;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.service.UserService;

/**
 * User account endpoints.
 *
 * <h2>Two endpoints were removed rather than repaired</h2>
 * {@code POST /api/users} persisted the submitted password without hashing,
 * producing accounts that could never sign in, and duplicated
 * {@code /api/auth/signup}. {@code PUT /api/users/{id}} bound the {@code User}
 * entity directly and saved it under the path's id with no ownership check --
 * mass assignment and IDOR together, so any caller could rewrite any account's
 * credentials. Both were publicly reachable.
 *
 * <p>Neither had a safe minimal repair: a correct update endpoint needs a
 * narrow request type and a separate password-change flow that requires the
 * current password. They are removed now and return with that work rather than
 * being left in a half-fixed state.
 *
 * <p>Access rules live in {@code SecurityConfig}: {@code /api/users/me} needs
 * only authentication, everything else under {@code /api/users} needs ADMIN.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * The authenticated caller's own account.
     *
     * <p>{@code @AuthenticationPrincipal} injects the principal that
     * {@code JwtAuthenticationFilter} placed in the security context. Note that
     * there is no id in the path: the identity comes from the verified token, so
     * there is nothing for a caller to tamper with.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(@AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(UserResponse.from(principal));
    }

    /** ADMIN only. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    /** ADMIN only. */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(id)));
    }

    /** ADMIN only. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
