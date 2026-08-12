package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that the Flyway migration produced a schema that actually enforces
 * the invariants it claims to.
 *
 * <p>These assertions are about the database, not the application. A constraint
 * that exists only in Java is advisory -- it protects nothing against a second
 * writer, a background job, or a direct SQL session. Asserting the constraints
 * here proves the last line of defence is real.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM transaction_tags");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM users WHERE email LIKE 'schema-test%'");
    }

    @Test
    @DisplayName("Flyway applied the baseline migration successfully")
    void flywayAppliedBaseline() {
        var rows = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0))
                .containsEntry("version", "1")
                .containsEntry("success", true);
    }

    @Test
    @DisplayName("Hibernate validated its mappings against the migrated schema")
    void hibernateValidatesAgainstMigratedSchema() {
        // Reaching this point at all is the assertion: ddl-auto=validate runs
        // during context startup, so a drift between an entity and a migration
        // would have failed the context before any test executed.
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("users.email is NOT NULL, so a nullable UNIQUE cannot be bypassed")
    void emailIsNotNull() {
        // The original mapping left email nullable while UNIQUE. PostgreSQL
        // permits unlimited NULLs in a unique index, so that combination did
        // not enforce "at most one user without an email".
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password) VALUES (NULL, 'schema-test-a', 'x')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("users.email is unique")
    void emailIsUnique() {
        jdbc.update("INSERT INTO users (email, username, password) VALUES (?, ?, ?)",
                "schema-test-dupe@example.com", "schema-test-b", "x");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password) VALUES (?, ?, ?)",
                "schema-test-dupe@example.com", "schema-test-c", "x"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("users.username is unique")
    void usernameIsUnique() {
        jdbc.update("INSERT INTO users (email, username, password) VALUES (?, ?, ?)",
                "schema-test-1@example.com", "schema-test-dupe", "x");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, username, password) VALUES (?, ?, ?)",
                "schema-test-2@example.com", "schema-test-dupe", "x"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("transactions.type only accepts declared enum values")
    void transactionTypeCheckConstraintRejectsUnknownValues() {
        // Enums are persisted as strings. Without a CHECK constraint the column
        // would accept any string written by a route that bypasses the
        // application -- a data-repair script, for instance.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions
                    (user_id, property_id, property_name, type, category,
                     description, transaction_amount, recurring, date)
                VALUES ('1', 1, 'Test', 'NOT_A_TYPE', 'CLEANING', 'x', 1.0, false, now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("transaction_tags cannot reference a transaction that does not exist")
    void transactionTagsForeignKeyIsEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO transaction_tags (transaction_id, tag) VALUES (999999, 'orphan')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("foreign key columns the application traverses are indexed")
    void foreignKeyColumnsAreIndexed() {
        // PostgreSQL does not index foreign keys automatically. Without an
        // index, every child lookup and every parent DELETE scans the child
        // table sequentially.
        var indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class);

        assertThat(indexes).contains(
                "ix_transaction_tags_transaction_id",
                "ix_transaction_warranties_transaction_id",
                "ix_bookings_property_id");
    }
}
