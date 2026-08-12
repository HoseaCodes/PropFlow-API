package com.hoseacodes.propflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hoseacodes.propflow.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Case-insensitive lookup used for sign-in.
     *
     * <p>Written as an explicit {@code lower(...)} comparison rather than
     * Spring Data's {@code findByUsernameIgnoreCase} derivation, which emits
     * {@code upper(username) = upper(?)}. The unique index created in V3 is on
     * {@code lower(username)}, and PostgreSQL can only use a functional index
     * when the predicate has the same shape -- an {@code upper()} predicate
     * would silently fall back to a sequential scan.
     */
    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoringCase(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoringCase(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoringCase(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoringCase(@Param("username") String username);
}
