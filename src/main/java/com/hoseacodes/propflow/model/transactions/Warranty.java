package com.hoseacodes.propflow.model.transactions;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A warranty attached to a purchase, stored in the transaction_warranties table. */
@Getter
@Setter
@NoArgsConstructor
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
