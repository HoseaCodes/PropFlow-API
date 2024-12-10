package com.airbnb.model.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import java.util.Date;

@Data
@Embeddable
public class Warranty {
    @Column(name = "warranty_start_date")
    private Date startDate;
    
    @Column(name = "warranty_end_date")
    private Date endDate;
    
    @Column(name = "warranty_description")
    private String description;
    
    @Column(name = "warranty_document_url")
    private String documentUrl;
}