package com.hoseacodes.propflow.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoseacodes.propflow.dto.request.SignUpRequest;
import com.hoseacodes.propflow.exception.DuplicateResourceException;
import com.hoseacodes.propflow.exception.ResourceNotFoundException;
import com.hoseacodes.propflow.model.Role;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.repository.UserRepository;

/**
 * User account operations.
 *
 * <h2>Registration has exactly one entry point</h2>
 * There were previously two ways to create a user: {@code /api/auth/signup},
 * which hashed the password, and {@code POST /api/users}, which saved the raw
 * string straight to the database. Accounts created the second way could never
 * sign in, because authentication compared the submitted password against a
 * value that was not a BCrypt hash.
 *
 * <p>The lesson is structural rather than incidental: the encoding step lived
 * in a controller, so a second controller bypassed it. A security control
 * enforced at one call site will eventually be missed at another. Password
 * encoding now happens here, in the only method that persists a new user, so no
 * caller can skip it.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new account with the {@link Role#USER} role.
     *
     * <p>The role is assigned here rather than taken from the request. Reading
     * it from client input would let anyone register as an administrator.
     *
     * <p>The uniqueness checks below are a courtesy that produces a clean 409
     * instead of a constraint-violation stack trace. They are not the
     * guarantee: two concurrent registrations for the same email can both pass
     * the check before either commits. The unique indexes on
     * {@code users.email} and {@code users.username} are what actually enforce
     * it, and the resulting {@code DataIntegrityViolationException} is mapped to
     * 409 as well. Check-then-act is inherently racy; the database constraint is
     * the real invariant.
     */
    @Transactional
    public User register(SignUpRequest request) {
        if (userRepository.existsByEmailIgnoringCase(request.email())) {
            throw new DuplicateResourceException("An account with that email already exists");
        }
        if (userRepository.existsByUsernameIgnoringCase(request.username())) {
            throw new DuplicateResourceException("An account with that username already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(Role.USER);

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User getByUsername(String username) {
        return userRepository.findByUsernameIgnoringCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("No account found for the current principal"));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        userRepository.deleteById(id);
    }
}
