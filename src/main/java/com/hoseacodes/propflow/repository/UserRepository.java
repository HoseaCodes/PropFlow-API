package com.hoseacodes.propflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hoseacodes.propflow.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    Optional<User>  findByUsername(String username);
}