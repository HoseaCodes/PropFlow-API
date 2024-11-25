package com.airbnb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.airbnb.model.Property;

public interface PropertyRepository extends JpaRepository<Property, Long> {
}