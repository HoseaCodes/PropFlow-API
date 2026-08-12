package com.hoseacodes.propflow.model.transactions;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Refund details for a transaction, embedded into the transactions table. */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class RefundInfo {

    /** Named refundAmount rather than amount to avoid colliding with the parent. */
    @Column(name = "refund_amount", precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_date")
    private Date refundDate;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refund_reference")
    private String refundReference;
}
