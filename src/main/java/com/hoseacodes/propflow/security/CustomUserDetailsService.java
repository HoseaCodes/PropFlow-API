package com.hoseacodes.propflow.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoseacodes.propflow.repository.UserRepository;

/**
 * Loads the Spring Security principal for a username.
 *
 * <p>Used both by {@code DaoAuthenticationProvider} during sign-in and by
 * {@link JwtAuthenticationFilter} on every authenticated request.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // The message is intentionally generic and the username is not logged
        // at error level. The previous implementation logged
        // "User not found: {username}" on every failed sign-in, which turns the
        // application log into a record of attempted usernames -- useful to
        // anyone who can read logs, and noisy under credential-stuffing traffic.
        return userRepository.findByUsernameIgnoringCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
