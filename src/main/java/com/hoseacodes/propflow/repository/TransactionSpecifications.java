package com.hoseacodes.propflow.repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.hoseacodes.propflow.model.transactions.PaymentMethod;
import com.hoseacodes.propflow.model.transactions.Transaction;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionFrequency;
import com.hoseacodes.propflow.model.transactions.TransactionStatus;
import com.hoseacodes.propflow.model.transactions.TransactionType;

/**
 * Composable {@link Specification} factories for transaction search.
 *
 * <p>The previous implementation was one 78-line lambda that built every
 * predicate inline. Small named factories are testable in isolation,
 * recombinable, and each reads as the business question it answers. They also
 * make it obvious when a predicate is missing, which a wall of {@code if}
 * statements does not.
 *
 * <p>Every factory returns {@code null} when its input is absent.
 * {@code Specification.allOf} skips nulls, so callers can compose
 * unconditionally instead of guarding each one.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Restricts results to one owner.
     *
     * <p>This is an authorization control, not a user-facing filter. It is
     * composed into every read from the authenticated principal and is
     * deliberately not exposed on the search request type -- a scope a client
     * can supply is a filter, and a filter can be omitted.
     *
     * <p>Compares on {@code user.id} rather than navigating to the User entity,
     * so the generated SQL uses the {@code user_id} foreign key column directly
     * and matches the leading column of ix_transactions_user_id_date. No join is
     * emitted.
     */
    public static Specification<Transaction> ownedBy(Long userId) {
        return userId == null ? null
                : (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> forProperty(Long propertyId) {
        return propertyId == null ? null
                : (root, query, cb) -> cb.equal(root.get("property").get("id"), propertyId);
    }

    public static Specification<Transaction> hasId(Long id) {
        return id == null ? null
                : (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<Transaction> dateFrom(Date start) {
        return start == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), start);
    }

    public static Specification<Transaction> dateTo(Date end) {
        return end == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), end);
    }

    public static Specification<Transaction> amountAtLeast(BigDecimal min) {
        return min == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Transaction> amountAtMost(BigDecimal max) {
        return max == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    public static Specification<Transaction> ofType(TransactionType type) {
        return type == null ? null
                : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> inCategory(TransactionCategory category) {
        return category == null ? null
                : (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Transaction> withStatus(TransactionStatus status) {
        return status == null ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> paidWith(PaymentMethod method) {
        return method == null ? null
                : (root, query, cb) -> cb.equal(root.get("paymentMethod"), method);
    }

    public static Specification<Transaction> isRecurring(Boolean recurring) {
        return recurring == null ? null
                : (root, query, cb) -> cb.equal(root.get("recurring"), recurring);
    }

    public static Specification<Transaction> withFrequency(TransactionFrequency frequency) {
        return frequency == null ? null
                : (root, query, cb) -> cb.equal(root.get("frequency"), frequency);
    }

    public static Specification<Transaction> vendorContains(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(fragment.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("vendor")), pattern, '\\');
    }

    public static Specification<Transaction> withApprovalStatus(String approvalStatus) {
        return approvalStatus == null || approvalStatus.isBlank() ? null
                : (root, query, cb) -> cb.equal(root.get("approvalStatus"), approvalStatus);
    }

    /** Past its due date and not yet paid. */
    public static Specification<Transaction> overdue(Boolean overdue) {
        if (overdue == null || !overdue) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueDate")),
                cb.lessThan(root.get("dueDate"), new Date()),
                cb.isNull(root.get("paidAt")));
    }

    /**
     * Free-text search across the descriptive columns.
     *
     * <p>{@code LIKE '%term%'} cannot use a standard B-tree index, so this is a
     * sequential scan whose cost grows with the table. Acceptable at this scale
     * and for an explicitly opt-in filter; the upgrade path when it stops being
     * acceptable is a PostgreSQL full-text index (tsvector + GIN), which is a
     * schema change rather than a rewrite of this method.
     */
    public static Specification<Transaction> matchesText(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("description")), pattern, '\\'),
                cb.like(cb.lower(root.get("notes")), pattern, '\\'),
                cb.like(cb.lower(root.get("vendor")), pattern, '\\'),
                cb.like(cb.lower(root.get("paymentReference")), pattern, '\\'));
    }

    /**
     * Escapes LIKE wildcards in user input.
     *
     * <p>Not an injection defence -- the value is still bound as a parameter, so
     * SQL injection is not possible either way. This is about correctness: a
     * user searching for "50%" means the literal characters, and unescaped that
     * {@code %} becomes a wildcard matching everything after "50".
     */
    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
