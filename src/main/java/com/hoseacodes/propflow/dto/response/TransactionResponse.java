package com.hoseacodes.propflow.dto.response;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.hoseacodes.propflow.model.transactions.PaymentMethod;
import com.hoseacodes.propflow.model.transactions.RefundInfo;
import com.hoseacodes.propflow.model.transactions.TaxDetails;
import com.hoseacodes.propflow.model.transactions.Transaction;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionFrequency;
import com.hoseacodes.propflow.model.transactions.TransactionStatus;
import com.hoseacodes.propflow.model.transactions.TransactionType;

/**
 * Full transaction representation, returned for a single record.
 *
 * <p>Includes the lazy collections. This mapping must therefore run inside the
 * transaction that loaded the entity -- with {@code open-in-view=false} the
 * persistence context closes at the service boundary, so the service maps and
 * the controller only serialises. That constraint is the reason mapping lives
 * in the service rather than the controller.
 */
public record TransactionResponse(
        Long id,
        Long propertyId,
        String propertyName,
        String bookingReference,
        Long bookingId,
        TransactionType type,
        TransactionCategory category,
        String subcategory,
        String description,
        BigDecimal amount,
        TransactionStatus status,
        PaymentMethod paymentMethod,
        String paymentReference,
        Boolean recurring,
        TransactionFrequency frequency,
        String vendor,
        String receiptUrl,
        String notes,
        TaxDetailsResponse taxDetails,
        RefundResponse refund,
        List<String> tags,
        Map<String, String> metadata,
        String approvalStatus,
        String approvedBy,
        Date approvedDate,
        Date date,
        Date dueDate,
        Date paidAt,
        Date createdAt,
        Date updatedAt
) {

    public record TaxDetailsResponse(
            Boolean taxable,
            String taxCategory,
            BigDecimal taxAmount,
            Boolean deductible,
            String deductionCategory) {

        static TaxDetailsResponse from(TaxDetails details) {
            return details == null ? null : new TaxDetailsResponse(
                    details.getTaxable(),
                    details.getTaxCategory(),
                    details.getTaxAmount(),
                    details.getDeductible(),
                    details.getDeductionCategory());
        }
    }

    public record RefundResponse(
            BigDecimal refundAmount,
            Date refundDate,
            String refundReason,
            String refundReference) {

        static RefundResponse from(RefundInfo refund) {
            return refund == null ? null : new RefundResponse(
                    refund.getRefundAmount(),
                    refund.getRefundDate(),
                    refund.getRefundReason(),
                    refund.getRefundReference());
        }
    }

    /** Must be called while the entity's persistence context is still open. */
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getPropertyId(),
                t.getPropertyName(),
                t.getBookingReference(),
                t.getBookingId(),
                t.getType(),
                t.getCategory(),
                t.getSubcategory(),
                t.getDescription(),
                t.getAmount(),
                t.getStatus(),
                t.getPaymentMethod(),
                t.getPaymentReference(),
                t.getRecurring(),
                t.getFrequency(),
                t.getVendor(),
                t.getReceiptUrl(),
                t.getNotes(),
                TaxDetailsResponse.from(t.getTaxDetails()),
                RefundResponse.from(t.getRefund()),
                List.copyOf(t.getTags() == null ? List.of() : t.getTags()),
                Map.copyOf(t.getMetadata() == null ? Map.of() : t.getMetadata()),
                t.getApprovalStatus(),
                t.getApprovedBy(),
                t.getApprovedDate(),
                t.getDate(),
                t.getDueDate(),
                t.getPaidAt(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
