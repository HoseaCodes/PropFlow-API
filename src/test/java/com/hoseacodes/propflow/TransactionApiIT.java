package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hoseacodes.propflow.dto.request.PropertyRequest;
import com.hoseacodes.propflow.dto.request.TransactionRequest;
import com.hoseacodes.propflow.dto.request.TransactionSearchRequest;
import com.hoseacodes.propflow.model.transactions.PaymentMethod;
import com.hoseacodes.propflow.model.transactions.Transaction;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionStatus;
import com.hoseacodes.propflow.model.transactions.TransactionType;
import com.hoseacodes.propflow.repository.PropertyRepository;
import com.hoseacodes.propflow.repository.TransactionRepository;

/**
 * End-to-end coverage of the transaction endpoints.
 *
 * <p>The search tests matter most. The filtering was completely broken -- a
 * fully-built {@code Specification} was constructed and then discarded -- and
 * the endpoint still returned a well-formed page, so nothing looked wrong. A
 * unit test with a mocked repository could not have caught it: the mock would
 * return the stubbed list whether or not the specification was applied. Only a
 * real database, seeded with rows that should and should not match, exposes it.
 */
class TransactionApiIT extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String auth;
    private Long propertyId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM transaction_tags");
        jdbc.update("DELETE FROM transaction_warranties");
        jdbc.update("DELETE FROM transaction_metadata");
        transactionRepository.deleteAll();
        propertyRepository.deleteAll();

        auth = registerAndSignIn("txn-user-" + System.nanoTime());
        propertyId = createProperty("Bishop Arts Bungalow");
    }

    private Long createProperty(String name) throws Exception {
        String body = objectMapper.writeValueAsString(new PropertyRequest(
                name, "123 N Bishop Ave", null, new BigDecimal("189.50"),
                4, 2, 1, true, "STR-1", null, null));

        String created = mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(created).get("id").asLong();
    }

    private static Date daysAgo(int days) {
        return Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    private TransactionRequest expense(String description, String amount, Date date) {
        return expense(description, amount, date, "Acme Plumbing");
    }

    private TransactionRequest expense(String description, String amount, Date date,
                                       String vendor) {
        return new TransactionRequest(
                propertyId, TransactionType.EXPENSE, TransactionCategory.MAINTENANCE,
                null, description, new BigDecimal(amount), date,
                TransactionStatus.PAID, PaymentMethod.CREDIT_CARD, null,
                false, null, vendor, null, null, null, null, null, null,
                List.of(), Map.of());
    }

    private Long createTransaction(TransactionRequest request) throws Exception {
        String created = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private String searchBody(TransactionSearchRequest request) throws Exception {
        return mockMvc.perform(post("/api/transactions/search")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static TransactionSearchRequest blankSearch() {
        return new TransactionSearchRequest(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("returns 201 and persists the transaction against the caller")
        void createsTransaction() throws Exception {
            Long id = createTransaction(expense("Fix leaking tap", "125.40", daysAgo(1)));

            mockMvc.perform(get("/api/transactions/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Fix leaking tap"))
                    .andExpect(jsonPath("$.amount").value(125.40))
                    // Resolved server-side from the property, not client input.
                    .andExpect(jsonPath("$.propertyName").value("Bishop Arts Bungalow"));
        }

        @Test
        @DisplayName("stores money exactly, without floating point drift")
        void moneyIsExact() throws Exception {
            createTransaction(expense("Cent test A", "0.10", daysAgo(1)));
            createTransaction(expense("Cent test B", "0.20", daysAgo(1)));

            // The sum is computed by PostgreSQL over NUMERIC. As DOUBLE
            // PRECISION this would have been 0.30000000000000004.
            BigDecimal total = jdbc.queryForObject(
                    "SELECT SUM(transaction_amount) FROM transactions", BigDecimal.class);

            assertThat(total).usingComparator(BigDecimal::compareTo)
                    .isEqualTo(new BigDecimal("0.30"));
        }

        @Test
        @DisplayName("rejects an income category on an expense with 422")
        void enforcesCategoryTypePairing() throws Exception {
            // The rule TransactionCategory.isValidForType has always encoded and
            // nothing ever called. Every field is individually valid, so this
            // is 422 rather than 400.
            TransactionRequest invalid = new TransactionRequest(
                    propertyId, TransactionType.EXPENSE, TransactionCategory.BOOKING_PAYMENT,
                    null, "Miscategorised", new BigDecimal("10.00"), daysAgo(1),
                    null, null, null, false, null, null, null, null, null, null, null, null,
                    List.of(), Map.of());

            mockMvc.perform(post("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Business rule violation"));

            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        @DisplayName("rejects a mortgage categorised as income with 422")
        void rejectsExpenseCategoryOnIncome() throws Exception {
            TransactionRequest invalid = new TransactionRequest(
                    propertyId, TransactionType.INCOME, TransactionCategory.MORTGAGE,
                    null, "Mortgage as revenue", new BigDecimal("1500.00"), daysAgo(1),
                    null, null, null, false, null, null, null, null, null, null, null, null,
                    List.of(), Map.of());

            mockMvc.perform(post("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("rejects a zero or negative amount with 400")
        void rejectsNonPositiveAmount() throws Exception {
            for (String amount : new String[]{"0.00", "-5.00"}) {
                mockMvc.perform(post("/api/transactions")
                                .header(HttpHeaders.AUTHORIZATION, auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        expense("Bad amount", amount, daysAgo(1)))))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.amount").exists());
            }
        }

        @Test
        @DisplayName("rejects missing required fields with 400")
        void rejectsMissingFields() throws Exception {
            mockMvc.perform(post("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.propertyId").exists())
                    .andExpect(jsonPath("$.errors.type").exists())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }

        @Test
        @DisplayName("rejects a reference to a property that does not exist with 404")
        void rejectsUnknownProperty() throws Exception {
            TransactionRequest orphan = new TransactionRequest(
                    999999L, TransactionType.EXPENSE, TransactionCategory.CLEANING,
                    null, "Orphan", new BigDecimal("10.00"), daysAgo(1),
                    null, null, null, false, null, null, null, null, null, null, null, null,
                    List.of(), Map.of());

            mockMvc.perform(post("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(orphan)))
                    .andExpect(status().isNotFound());
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("search")
    class Search {

        @BeforeEach
        void seed() throws Exception {
            createTransaction(expense("Plumbing repair", "125.40", daysAgo(2), "Acme Plumbing"));
            createTransaction(expense("Deep clean", "80.00", daysAgo(30), "Sparkle Cleaners"));

            TransactionRequest income = new TransactionRequest(
                    propertyId, TransactionType.INCOME, TransactionCategory.BOOKING_PAYMENT,
                    null, "Weekend stay", new BigDecimal("450.00"), daysAgo(5),
                    TransactionStatus.PAID, PaymentMethod.BANK_TRANSFER, null,
                    false, null, "Airbnb", null, null, null, null, null, null,
                    List.of(), Map.of());
            createTransaction(income);
        }

        @Test
        @DisplayName("filtering by type actually narrows the result set")
        void filtersByType() throws Exception {
            // The regression test for the discarded Specification. Before the
            // fix this returned all three rows while reporting success.
            var request = new TransactionSearchRequest(null, null, null, null,
                    TransactionType.INCOME, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null);

            String body = searchBody(request);

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(1);
            assertThat(objectMapper.readTree(body).get("content").get(0)
                    .get("description").asText()).isEqualTo("Weekend stay");
        }

        @Test
        @DisplayName("an empty filter returns everything")
        void emptyFilterReturnsAll() throws Exception {
            String body = searchBody(blankSearch());
            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("filtering by amount range narrows the result set")
        void filtersByAmountRange() throws Exception {
            var request = new TransactionSearchRequest(null, null,
                    new BigDecimal("100.00"), new BigDecimal("200.00"),
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null);

            String body = searchBody(request);

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(1);
            assertThat(objectMapper.readTree(body).get("content").get(0)
                    .get("description").asText()).isEqualTo("Plumbing repair");
        }

        @Test
        @DisplayName("filtering by date range narrows the result set")
        void filtersByDateRange() throws Exception {
            var request = new TransactionSearchRequest(daysAgo(7), new Date(),
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null);

            String body = searchBody(request);

            // Excludes the 30-day-old deep clean.
            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("free-text search matches on description")
        void filtersBySearchTermOnDescription() throws Exception {
            var request = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, "deep clean",
                    null, null, null, null);

            String body = searchBody(request);

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("free-text search also matches on vendor, not only description")
        void filtersBySearchTermAcrossFields() throws Exception {
            // "sparkle" appears only in the vendor of the deep clean, never in
            // any description -- so a match proves the OR spans fields rather
            // than searching description alone.
            var request = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, "sparkle",
                    null, null, null, null);

            String body = searchBody(request);

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(1);
            assertThat(objectMapper.readTree(body).get("content").get(0)
                    .get("description").asText()).isEqualTo("Deep clean");
        }

        @Test
        @DisplayName("LIKE wildcards in user input are treated as literals")
        void escapesLikeWildcards() throws Exception {
            // Unescaped, "%" would match every row. A user searching for a
            // percent sign means the character.
            var request = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, "%",
                    null, null, null, null);

            String body = searchBody(request);

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("combined filters apply together")
        void combinesFilters() throws Exception {
            var request = new TransactionSearchRequest(daysAgo(7), new Date(), null, null,
                    TransactionType.EXPENSE, null, propertyId, null, null, null, null, null,
                    null, null, null, null, null, null, null);

            String body = searchBody(request);

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(1);
            assertThat(objectMapper.readTree(body).get("content").get(0)
                    .get("description").asText()).isEqualTo("Plumbing repair");
        }

        @Test
        @DisplayName("rejects a sort field that is not whitelisted")
        void rejectsUnknownSortField() throws Exception {
            var request = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    "password", null);

            mockMvc.perform(post("/api/transactions/search")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("sorts by an allowed field in the requested direction")
        void sortsByAllowedField() throws Exception {
            var request = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    "amount", "desc");

            String body = searchBody(request);

            var content = objectMapper.readTree(body).get("content");
            assertThat(content.get(0).get("amount").decimalValue())
                    .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("450.00"));
        }

        @Test
        @DisplayName("caps an oversized page size")
        void capsPageSize() throws Exception {
            var request = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, 0, 5000, null, null);

            mockMvc.perform(post("/api/transactions/search")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.size").exists());
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("an update preserves fields the request does not manage")
        void updatePreservesServerManagedFields() throws Exception {
            Long id = createTransaction(expense("Original", "100.00", daysAgo(3)));

            Transaction before = transactionRepository.findById(id).orElseThrow();
            Date originalCreatedAt = before.getCreatedAt();

            mockMvc.perform(put("/api/transactions/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    expense("Updated", "150.00", daysAgo(3)))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Updated"));

            Transaction after = transactionRepository.findById(id).orElseThrow();

            // The old implementation set the id on a client-supplied object and
            // called save(), replacing the whole row and nulling everything the
            // request omitted.
            assertThat(after.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(after.getUserId()).isEqualTo(before.getUserId());
            assertThat(after.getPropertyName()).isEqualTo("Bishop Arts Bungalow");
        }

        @Test
        @DisplayName("updating an unknown transaction returns 404")
        void updateUnknownIsNotFound() throws Exception {
            mockMvc.perform(put("/api/transactions/{id}", 999999)
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    expense("Nope", "10.00", daysAgo(1)))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("delete returns 204 and removes the transaction")
        void deletes() throws Exception {
            Long id = createTransaction(expense("Delete me", "10.00", daysAgo(1)));

            mockMvc.perform(delete("/api/transactions/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isNoContent());

            assertThat(transactionRepository.findById(id)).isEmpty();
        }

        @Test
        @DisplayName("deleting an unknown transaction returns 404")
        void deleteUnknownIsNotFound() throws Exception {
            mockMvc.perform(delete("/api/transactions/{id}", 999999)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isNotFound());
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("listing is paginated")
        void listIsPaginated() throws Exception {
            for (int i = 0; i < 3; i++) {
                createTransaction(expense("Item " + i, "10.00", daysAgo(i + 1)));
            }

            mockMvc.perform(get("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.last").value(false));
        }

        @Test
        @DisplayName("a summary listing does not trigger a query per row")
        void listingDoesNotNPlusOne() throws Exception {
            for (int i = 0; i < 5; i++) {
                createTransaction(expense("Item " + i, "10.00", daysAgo(i + 1)));
            }

            var statistics = entityManagerFactory.unwrap(
                    org.hibernate.SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            mockMvc.perform(get("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5));

            // One query for the page plus one for the count. The three element
            // collections were EAGER, which made this 1 + 3N; the summary
            // response omits them and they are now LAZY.
            assertThat(statistics.getPrepareStatementCount())
                    .as("queries issued for a 5-row page")
                    .isLessThanOrEqualTo(2);
        }
    }
}
