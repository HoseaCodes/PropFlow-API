package com.airbnb.model.transactions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private Long propertyId;
    
    @Column(nullable = false)
    private String propertyName;
    
    @Column(name = "booking_reference")
    private String bookingReference;
    
    @Column(name = "booking_id")
    private Long bookingId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionCategory category;
    
    private String subcategory;
    
    @Column(nullable = false)
    private String description;
    
    @Column(name = "transaction_amount", nullable = false)
    private Double amount;
    
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;
    
    @Column(name = "payment_reference")
    private String paymentReference;
    
    @Column(nullable = false)
    private Boolean recurring = false;
    
    @Enumerated(EnumType.STRING)
    private TransactionFrequency frequency;
    
    private String vendor;
    
    @Column(name = "receipt_url")
    private String receiptUrl;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "taxable", column = @Column(name = "tax_is_taxable")),
        @AttributeOverride(name = "taxCategory", column = @Column(name = "tax_category")),
        @AttributeOverride(name = "taxAmount", column = @Column(name = "tax_amount")),
        @AttributeOverride(name = "deductible", column = @Column(name = "tax_is_deductible")),
        @AttributeOverride(name = "deductionCategory", column = @Column(name = "tax_deduction_category"))
    })
    private TaxDetails taxDetails;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "transaction_tags",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @Column(name = "tag")
    private List<String> tags;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "transaction_warranties",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    private List<Warranty> warranties;
    
    @Column(name = "approval_status")
    private String approvalStatus;
    
    @Column(name = "approved_by")
    private String approvedBy;
    
    @Column(name = "approved_date")
    private Date approvedDate;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "refundAmount", column = @Column(name = "refund_amount")),
        @AttributeOverride(name = "refundDate", column = @Column(name = "refund_date")),
        @AttributeOverride(name = "refundReason", column = @Column(name = "refund_reason")),
        @AttributeOverride(name = "refundReference", column = @Column(name = "refund_reference"))
    })
    private RefundInfo refund;
    
    @Column(nullable = false)
    private Date date;
    
    @Column(name = "due_date")
    private Date dueDate;
    
    @Column(name = "paid_at")
    private Date paidAt;
    
    @Column(name = "created_at", updatable = false)
    private Date createdAt;
    
    @Column(name = "updated_at")
    private Date updatedAt;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "transaction_metadata",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (recurring == null) {
            recurring = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    // Helper method to update metadata
    public void updateMetadata(Map<String, String> newMetadata) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        if (newMetadata != null) {
            this.metadata.clear();  // Clear existing metadata
            this.metadata.putAll(newMetadata);  // Add new metadata
        }
    }
}
