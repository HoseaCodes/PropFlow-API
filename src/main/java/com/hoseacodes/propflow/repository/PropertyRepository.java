package com.hoseacodes.propflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoseacodes.propflow.model.Property;

public interface PropertyRepository extends JpaRepository<Property, Long> {
}