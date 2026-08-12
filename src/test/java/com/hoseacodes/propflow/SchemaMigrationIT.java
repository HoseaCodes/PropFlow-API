package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that the Flyway migrations produced a schema that actually enforces
 * the invariants they claim to.
 *
 * <p>These assertions are about the database, not the application. A constraint
 * that exists only in Java is advisory -- it protects nothing against a second
 * writer, a background job, or a direct SQL session. Asserting the constraints
 * here proves the last line of defence is real.
 *
 * <p>Statements are issued through {@link JdbcTemplate} rather than the
 * repositories, deliberately: the point is what happens when something bypasses
 * the application entirely.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;
    private Long propertyId;

    @BeforeEach
    void seedValidParents() {
        resetDatabase();

        // Real parent rows, so that a failed insert below is attributable to the
        // constraint under test rather than to an incidental foreign-key
        // violation. Without this, a test can pass for entirely the wrong reason.
        userId = jdbc.queryForObject("""
                INSERT INTO users (email, username, password, role)
                VALUES ('schema-test@example.com', 'schema-test', 'x', 'USER')
                RETURNING id
                """, Long.class);

        propertyId = jdbc.queryForObject("""
                INSERT INTO properties (owner_id, name, address, base_price, active)
                VALUES (?, 'Test Property', 'Somewhere', 100.00, true)
                RETURNING id
                """, Long.class, userId);
    }

    @AfterEach
    void cleanUp() {
        resetDatabase();
    }

    private int insertTransaction(String type, String category, String amount,
                                  Long forUser, Long forProperty) {
        return jdbc.update("""
                INSERT INTO transactions
                    (user_id, property_id, property_name, type, category,
                     description, transaction_amount, recurring, date)
                VALUES (?, ?, 'Test Property', ?, ?, 'x', CAST(? AS NUMERIC), false, now())
                """, forUser, forProperty, type, category, amount);
    }

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Flyway applied every migration successfully")
    void flywayAppliedAllMigrations() {
        var rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row).containsEntry("success", true));
        assertThat(rows.get(0)).containsEntry("version", "1");
    }

    @Test
    @DisplayName("Hibernate validated its mappings against the migrated schema")
    void hibernateValidatesAgainstMigratedSchema() {
        // Reaching this point at all is the assertion: ddl-auto=validate runs
        // during context startup, so a drift between an entity and a migration
        // would have failed the context before any test executed.
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    // --- users -------------------------------------------------------------

    @Test
    @DisplayName("users.email is NOT NULL, so a nullable UNIQUE cannot be bypassed")
    void emailIsNotNull() {
        // Originally email was nullable and UNIQUE. PostgreSQL permits unlimited
        // NULLs in a unique index, so that pair did not enforce "at most one
        // user without an email".
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password, role) VALUES (NULL, 'a', 'x', 'USER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("email uniqueness is case-insensitive")
    void emailUniquenessIsCaseInsensitive() {
        // V3 replaced the case-sensitive UNIQUE constraint with a functional
        // unique index on lower(email). Without it the same person could hold
        // two accounts differing only in capitalisation.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password, role) VALUES (?, ?, 'x', 'USER')",
                "SCHEMA-TEST@example.com", "different-username"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("username uniqueness is case-insensitive")
    void usernameUniquenessIsCaseInsensitive() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password, role) VALUES (?, ?, 'x', 'USER')",
                "other@example.com", "SCHEMA-TEST"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("users.role only accepts declared values")
    void roleCheckConstraint() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password, role) VALUES (?, ?, 'x', 'superuser')",
                "role@example.com", "role-test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- ownership ---------------------------------------------------------

    @Test
    @DisplayName("a property cannot reference a user that does not exist")
    void propertyOwnerForeignKeyIsEnforced() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO properties (owner_id, name, address, base_price, active)
                VALUES (999999, 'Orphan', 'Nowhere', 10.00, true)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a user with properties cannot be deleted")
    void deletingUserWithPropertiesIsRestricted() {
        // ON DELETE RESTRICT rather than CASCADE. Removing an account must not
        // silently take its properties -- and through them its financial
        // history -- with it.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a property with transactions cannot be deleted")
    void deletingPropertyWithTransactionsIsRestricted() {
        insertTransaction("EXPENSE", "MAINTENANCE", "10.00", userId, propertyId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM properties WHERE id = ?", propertyId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- transactions ------------------------------------------------------

    @Test
    @DisplayName("a transaction cannot reference a property that does not exist")
    void transactionPropertyForeignKeyIsEnforced() {
        assertThatThrownBy(() ->
                insertTransaction("EXPENSE", "MAINTENANCE", "10.00", userId, 999999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a transaction cannot reference a user that does not exist")
    void transactionUserForeignKeyIsEnforced() {
        assertThatThrownBy(() ->
                insertTransaction("EXPENSE", "MAINTENANCE", "10.00", 999999L, propertyId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("transactions.type only accepts declared enum values")
    void transactionTypeCheckConstraintRejectsUnknownValues() {
        // Parent rows are valid here, so the only thing that can reject this
        // insert is the CHECK constraint itself.
        assertThatThrownBy(() ->
                insertTransaction("NOT_A_TYPE", "CLEANING", "10.00", userId, propertyId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Control: the same insert with a valid type succeeds.
        assertThatCode(() ->
                insertTransaction("EXPENSE", "CLEANING", "10.00", userId, propertyId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("transaction amounts must be positive")
    void amountMustBePositive() {
        // Direction is carried by the type column, not by the sign of the
        // amount, so a zero or negative amount is meaningless.
        for (String amount : new String[]{"0.00", "-1.00"}) {
            assertThatThrownBy(() ->
                    insertTransaction("EXPENSE", "MAINTENANCE", amount, userId, propertyId))
                    .as("amount %s", amount)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("money is stored as NUMERIC, not floating point")
    void moneyColumnsAreNumeric() {
        var type = jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'transactions' AND column_name = 'transaction_amount'
                """, String.class);

        assertThat(type).isEqualTo("numeric");
    }

    @Test
    @DisplayName("transaction_tags cannot reference a transaction that does not exist")
    void transactionTagsForeignKeyIsEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO transaction_tags (transaction_id, tag) VALUES (999999, 'orphan')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- indexes -----------------------------------------------------------

    @Test
    @DisplayName("foreign key columns the application traverses are indexed")
    void foreignKeyColumnsAreIndexed() {
        // PostgreSQL does not index foreign keys automatically. Without an
        // index, every child lookup and every parent DELETE -- which must check
        // for referencing rows -- scans the child table sequentially.
        var indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(indexes).contains(
                "ix_transaction_tags_transaction_id",
                "ix_transaction_warranties_transaction_id",
                "ix_bookings_property_id",
                "ix_properties_owner_id",
                "ix_transactions_user_id_date",
                "ix_transactions_property_id_date");
    }

    @Test
    @DisplayName("the owner-scoped listing index leads with user_id, then date")
    void transactionIndexColumnOrderSupportsScopedListing() {
        // Column order is the whole point of this index. Every transaction read
        // is "WHERE user_id = ? ORDER BY date DESC": user_id must lead so the
        // planner can seek to that user's slice, and date must follow so the
        // rows in that slice are already ordered and no sort step is needed.
        var definition = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'ix_transactions_user_id_date'
                """, String.class);

        assertThat(definition).contains("(user_id, date DESC)");
    }
}
