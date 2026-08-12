package com.hoseacodes.propflow.dto.response;

import java.math.BigDecimal;
import java.util.Date;

import com.hoseacodes.propflow.model.transactions.PaymentMethod;
import com.hoseacodes.propflow.model.transactions.Transaction;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionStatus;
import com.hoseacodes.propflow.model.transactions.TransactionType;

/**
 * Compact transaction representation used in list and search results.
 *
 * <p>Deliberately omits tags, warranties, and metadata. Those are lazy element
 * collections, so including them in a listing would issue up to three extra
 * queries per row -- the N+1 that made the old eagerly-fetched listing
 * expensive. Excluding them from the summary makes a page of results exactly
 * one query, and the detail endpoint serves the full record when a client
 * actually needs it.
 *
 * <p>That is a deliberate API shape, not a limitation: list views render
 * summaries, detail views render details.
 */
public record TransactionSummaryResponse(
        Long id,
        Long propertyId,
        String propertyName,
        TransactionType type,
        TransactionCategory category,
        String subcategory,
        String description,
        BigDecimal amount,
        TransactionStatus status,
        PaymentMethod paymentMethod,
        Boolean recurring,
        String vendor,
        Date date,
        Date dueDate,
        Date paidAt
) {

    public static TransactionSummaryResponse from(Transaction t) {
        return new TransactionSummaryResponse(
                t.getId(),
                // Reading the id off a lazy proxy does not initialise it,
                // so this costs no extra query.
                t.getProperty().getId(),
                t.getPropertyName(),
                t.getType(),
                t.getCategory(),
                t.getSubcategory(),
                t.getDescription(),
                t.getAmount(),
                t.getStatus(),
                t.getPaymentMethod(),
                t.getRecurring(),
                t.getVendor(),
                t.getDate(),
                t.getDueDate(),
                t.getPaidAt());
    }
}
