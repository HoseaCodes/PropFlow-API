package com.hoseacodes.propflow.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.model.User;

/**
 * Property persistence.
 *
 * <p>The owner-scoped finders exist so that authorization can be expressed as
 * part of the query rather than as a check after the fact. Loading a row and
 * then comparing its owner works only while every call site remembers to do it;
 * a query that cannot return another user's row is safe by construction, and a
 * forgotten scope shows up as an empty result rather than a data leak.
 */
public interface PropertyRepository extends JpaRepository<Property, Long> {

    Page<Property> findByOwner(User owner, Pageable pageable);

    Optional<Property> findByIdAndOwner(Long id, User owner);

    boolean existsByIdAndOwner(Long id, User owner);
}
