package com.airbnb.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "cleaning_checklists")
public class CleaningChecklist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
    
    private String taskName;
    private String description;
    private Boolean required;
    private Integer estimatedMinutes;
}