package com.hoseacodes.propflow.dto.request;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.hoseacodes.propflow.model.transactions.PaymentMethod;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionFrequency;
import com.hoseacodes.propflow.model.transactions.TransactionStatus;
import com.hoseacodes.propflow.model.transactions.TransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a transaction.
 *
 * <p>Absent by design: {@code id}, {@code userId}, {@code propertyName},
 * {@code createdAt}, {@code updatedAt}, and {@code version}.
 * <ul>
 *   <li>{@code userId} is taken from the authenticated principal. Accepting it
 *       from the body would let any caller file a transaction against another
 *       user's books.</li>
 *   <li>{@code propertyName} is a point-in-time snapshot the server resolves
 *       from the referenced property, not a client-supplied string.</li>
 *   <li>The timestamps and version are managed by JPA. The old endpoint bound
 *       the entity directly, so a client could backdate {@code createdAt} --
 *       which for an auditable financial record is exactly the field that must
 *       not be writable.</li>
 * </ul>
 *
 * <p>The type/category pairing rule is not expressible as a field annotation
 * because it spans two fields; it is enforced in the service and surfaces as
 * 422.
 */
public record TransactionRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        @NotNull(message = "type is required")
        TransactionType type,

        @NotNull(message = "category is required")
        TransactionCategory category,

        @Size(max = 255, message = "subcategory must be at most 255 characters")
        String subcategory,

        @NotBlank(message = "description is required")
        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        // Strictly positive: direction is carried by `type`, not by the sign of
        // the amount. A negative EXPENSE would be ambiguous, and the database
        // enforces the same rule via ck_transactions_amount_positive.
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "date is required")
        Date date,

        TransactionStatus status,
        PaymentMethod paymentMethod,

        @Size(max = 255, message = "paymentReference must be at most 255 characters")
        String paymentReference,

        Boolean recurring,
        TransactionFrequency frequency,

        @Size(max = 255, message = "vendor must be at most 255 characters")
        String vendor,

        @Size(max = 255, message = "receiptUrl must be at most 255 characters")
        String receiptUrl,

        String notes,

        Date dueDate,
        Date paidAt,

        @Size(max = 255, message = "bookingReference must be at most 255 characters")
        String bookingReference,

        Long bookingId,

        List<@Size(max = 255) String> tags,

        Map<String, String> metadata
) {
}
