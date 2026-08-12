package com.hoseacodes.propflow.model.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the income/expense classification rules on
 * {@link TransactionCategory}.
 *
 * <p>This is real domain logic rather than a getter: it decides which
 * categories may be paired with which transaction type, which is the closest
 * thing the model has to a business invariant.
 *
 * <p>Note that {@link TransactionCategory#isValidForType} is not currently
 * called by any production code path, so an INCOME transaction can still be
 * created with category MORTGAGE. These tests pin the rule's behaviour so that
 * wiring it into request validation is a safe change rather than a guess.
 *
 * <p>No Spring context and no database: the logic is pure, so the test should
 * be pure too.
 */
class TransactionCategoryTest {

    @Nested
    @DisplayName("income and expense classification")
    class Classification {

        @ParameterizedTest
        @EnumSource(value = TransactionCategory.class, names = {
                "BOOKING_PAYMENT", "CLEANING_FEE", "SECURITY_DEPOSIT",
                "LATE_CHECKOUT_FEE", "ADDITIONAL_GUEST_FEE", "OTHER_INCOME"})
        @DisplayName("revenue categories classify as income")
        void revenueCategoriesAreIncome(TransactionCategory category) {
            assertThat(category.isIncomeCategory()).isTrue();
            assertThat(category.isExpenseCategory()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = TransactionCategory.class, names = {
                "CLEANING", "MAINTENANCE", "UTILITIES", "MORTGAGE",
                "PROPERTY_TAX", "PLATFORM_FEES", "LEGAL_PROFESSIONAL"})
        @DisplayName("cost categories classify as expense")
        void costCategoriesAreExpense(TransactionCategory category) {
            assertThat(category.isExpenseCategory()).isTrue();
            assertThat(category.isIncomeCategory()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(TransactionCategory.class)
        @DisplayName("every category is exactly one of income or expense")
        void classificationIsTotalAndExclusive(TransactionCategory category) {
            // Guards the pairing as the enum grows: a category added in the
            // future is forced into exactly one bucket, and cannot silently
            // become both or neither.
            assertThat(category.isIncomeCategory())
                    .isNotEqualTo(category.isExpenseCategory());
        }
    }

    @Nested
    @DisplayName("isValidForType")
    class ValidForType {

        @Test
        @DisplayName("accepts an income category on an INCOME transaction")
        void acceptsMatchingIncome() {
            assertThat(TransactionCategory.isValidForType(
                    TransactionType.INCOME, TransactionCategory.BOOKING_PAYMENT)).isTrue();
        }

        @Test
        @DisplayName("accepts an expense category on an EXPENSE transaction")
        void acceptsMatchingExpense() {
            assertThat(TransactionCategory.isValidForType(
                    TransactionType.EXPENSE, TransactionCategory.MORTGAGE)).isTrue();
        }

        @Test
        @DisplayName("rejects an expense category on an INCOME transaction")
        void rejectsExpenseCategoryOnIncome() {
            // The case that motivates wiring this into validation: booking a
            // mortgage payment as revenue would inflate reported income.
            assertThat(TransactionCategory.isValidForType(
                    TransactionType.INCOME, TransactionCategory.MORTGAGE)).isFalse();
        }

        @Test
        @DisplayName("rejects an income category on an EXPENSE transaction")
        void rejectsIncomeCategoryOnExpense() {
            assertThat(TransactionCategory.isValidForType(
                    TransactionType.EXPENSE, TransactionCategory.BOOKING_PAYMENT)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(TransactionCategory.class)
        @DisplayName("every category is valid for exactly one transaction type")
        void eachCategoryIsValidForExactlyOneType(TransactionCategory category) {
            boolean validForIncome =
                    TransactionCategory.isValidForType(TransactionType.INCOME, category);
            boolean validForExpense =
                    TransactionCategory.isValidForType(TransactionType.EXPENSE, category);

            assertThat(validForIncome).isNotEqualTo(validForExpense);
        }
    }

    @Test
    @DisplayName("every category exposes a human-readable display name")
    void displayNamesArePresent() {
        for (TransactionCategory category : TransactionCategory.values()) {
            assertThat(category.getDisplayName())
                    .as("display name for %s", category)
                    .isNotBlank();
        }
    }
}
