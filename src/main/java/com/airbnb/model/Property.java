package com.airbnb.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "properties")
public class Property {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String address;
    private String description;
    private BigDecimal basePrice;
    private Integer maxGuests;
    private Integer bedrooms;
    private Integer bathrooms;
    private Boolean active;
    private String strPermitNumber;
    
    @Column(columnDefinition = "TEXT")
    private String houseRules;
    
    @Column(columnDefinition = "TEXT")
    private String checkInInstructions;
}
