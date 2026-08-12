package com.hoseacodes.propflow.model.transactions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A financial record against a property: income received or an expense
 * incurred.
 *
 * <h2>Fetch strategy</h2>
 * The three element collections are {@code LAZY}. They were {@code EAGER},
 * which meant loading N transactions issued one query for the transactions plus
 * up to 3N more for their collections -- the classic N+1, compounded by the
 * listing endpoint having no pagination. Lazy is the correct default for a
 * collection: pay for it where it is needed, not on every read.
 *
 * <p>The consequence, with {@code spring.jpa.open-in-view=false}, is that these
 * collections can only be traversed inside the transaction that loaded the
 * entity. That is why the service maps to DTOs before returning rather than
 * handing entities to the controller.
 *
 * <h2>Money</h2>
 * All amounts are {@link BigDecimal} over {@code NUMERIC(19,2)}. See V5 for why
 * {@code Double} was wrong.
 */
@Getter
@Setter
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // AUDIT H10: user_id is a String while users.id is a Long, and neither this
    // nor propertyId has a foreign key. Corrected in a later migration together
    // with the ownership model.
    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long propertyId;

    /**
     * The property's name as it was when the transaction was recorded.
     *
     * <p>Intentionally denormalised. A financial record should show the name in
     * force at the time, so a later rename does not silently rewrite history on
     * past statements. This is a point-in-time snapshot, not a cache of the
     * current value, and must not be refreshed.
     */
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

    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "transaction_tags",
            joinColumns = @JoinColumn(name = "transaction_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "transaction_warranties",
            joinColumns = @JoinColumn(name = "transaction_id"))
    private List<Warranty> warranties = new ArrayList<>();

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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "transaction_metadata",
            joinColumns = @JoinColumn(name = "transaction_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();
        createdAt = now;
        updatedAt = now;
        if (recurring == null) {
            recurring = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

    /**
     * Replaces the metadata contents in place.
     *
     * <p>Mutates the existing collection rather than assigning a new one:
     * Hibernate tracks the instance it handed out, and replacing the reference
     * on a managed entity can raise "a collection with cascade=all-delete-orphan
     * was no longer referenced".
     */
    public void replaceMetadata(Map<String, String> newMetadata) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.clear();
        if (newMetadata != null) {
            this.metadata.putAll(newMetadata);
        }
    }

    /** Replaces the tag list in place, for the same reason as metadata. */
    public void replaceTags(List<String> newTags) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        this.tags.clear();
        if (newTags != null) {
            this.tags.addAll(newTags);
        }
    }

    /** Replaces the warranty list in place, for the same reason as metadata. */
    public void replaceWarranties(List<Warranty> newWarranties) {
        if (this.warranties == null) {
            this.warranties = new ArrayList<>();
        }
        this.warranties.clear();
        if (newWarranties != null) {
            this.warranties.addAll(newWarranties);
        }
    }
}
