package com.hoseacodes.propflow.dto.request;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

import com.hoseacodes.propflow.model.transactions.PaymentMethod;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionFrequency;
import com.hoseacodes.propflow.model.transactions.TransactionStatus;
import com.hoseacodes.propflow.model.transactions.TransactionType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Filter criteria for transaction search. Every field is optional; omitted
 * fields do not constrain the result.
 *
 * <p>Replaces the entity-package {@code TransactionFilter}, which mixed a
 * request shape into the domain model.
 */
public record TransactionSearchRequest(

        Date startDate,
        Date endDate,

        @PositiveOrZero(message = "minAmount must not be negative")
        BigDecimal minAmount,

        @PositiveOrZero(message = "maxAmount must not be negative")
        BigDecimal maxAmount,

        TransactionType type,
        TransactionCategory category,
        Long propertyId,
        TransactionStatus status,
        PaymentMethod paymentMethod,
        Boolean recurring,
        TransactionFrequency frequency,
        String vendor,
        String approvalStatus,
        Boolean overdue,
        String searchTerm,

        @Min(value = 0, message = "page must not be negative")
        Integer page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must be at most 100")
        Integer size,

        String sortBy,
        String sortDirection
) {

    /**
     * Sort fields a client may request.
     *
     * <p>A whitelist, not a convenience. {@code sortBy} is interpolated into a
     * JPA Criteria path, so an unvalidated value is at best a 500 from an
     * unknown attribute and at worst an information leak -- probing which field
     * names are accepted reveals the internal model. Enumerating the permitted
     * fields also documents which sorts the indexes actually support.
     */
    public static final Set<String> SORTABLE_FIELDS =
            Set.of("date", "amount", "createdAt", "updatedAt", "dueDate", "category", "type", "status");

    public static final String DEFAULT_SORT_FIELD = "date";

    /** Page index, defaulting to the first page. */
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    /** Page size, defaulting to 20 and hard-capped at 100. */
    public int sizeOrDefault() {
        if (size == null) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public boolean descending() {
        return "desc".equalsIgnoreCase(sortDirection);
    }
}
