package com.airbnb.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
    
    private String category; // CLEANING, MAINTENANCE, SUPPLIES, UTILITIES, etc.
    private String description;
    private BigDecimal amount;
    private LocalDate date;
    private Boolean recurring;
    private String frequency; // MONTHLY, QUARTERLY, YEARLY
}