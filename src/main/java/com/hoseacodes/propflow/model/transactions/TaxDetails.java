package com.hoseacodes.propflow.model.transactions;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tax treatment of a transaction, embedded into the transactions table.
 *
 * <p>Embedded rather than a separate entity: these fields have no identity or
 * lifecycle of their own, are always loaded with their transaction, and are
 * never queried independently. A separate table would add a join for no benefit.
 */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class TaxDetails {

    @Column(name = "is_taxable")
    private Boolean taxable;

    @Column(name = "tax_category")
    private String taxCategory;

    /** NUMERIC(19,2), not double -- see V5. */
    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "is_deductible")
    private Boolean deductible;

    @Column(name = "deduction_category")
    private String deductionCategory;
}
