package com.airbnb.model.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class TaxDetails {
    @Column(name = "is_taxable")
    private Boolean taxable;
    
    @Column(name = "tax_category")
    private String taxCategory;
    
    @Column(name = "tax_amount")
    private Double taxAmount;
    
    @Column(name = "is_deductible")
    private Boolean deductible;
    
    @Column(name = "deduction_category")
    private String deductionCategory;
}