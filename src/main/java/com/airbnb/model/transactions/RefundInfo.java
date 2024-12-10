package com.airbnb.model.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import java.util.Date;

@Data
@Embeddable
public class RefundInfo {
    @Column(name = "refund_amount") // Changed from 'amount' to avoid conflict
    private Double refundAmount;
    
    @Column(name = "refund_date")
    private Date refundDate;
    
    @Column(name = "refund_reason")
    private String refundReason;
    
    @Column(name = "refund_reference")
    private String refundReference;
}