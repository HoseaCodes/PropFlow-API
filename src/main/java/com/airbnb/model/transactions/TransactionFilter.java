package com.airbnb.model.transactions;

import lombok.Data;
import java.util.Date;

@Data
public class TransactionFilter {
    private Date startDate;
    private Date endDate;
    private TransactionType type;
    private TransactionCategory category;
    private Long propertyId;
    private Double minAmount;
    private Double maxAmount;
    private String searchTerm;
    private String userId;
    private TransactionStatus status;
    private PaymentMethod paymentMethod;
    private Boolean recurring;
    private TransactionFrequency frequency;
    private String vendor;
    
    // Additional filter options for advanced search
    private Boolean hasTaxDetails;
    private Boolean hasWarranty;
    private Boolean hasRefund;
    private String approvalStatus;
    private Boolean overdue;
    private Boolean needsApproval;
    
    // Pagination and sorting
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}