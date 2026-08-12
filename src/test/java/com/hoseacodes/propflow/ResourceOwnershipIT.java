package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.hoseacodes.propflow.dto.request.PropertyRequest;
import com.hoseacodes.propflow.dto.request.TransactionRequest;
import com.hoseacodes.propflow.dto.request.TransactionSearchRequest;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.model.transactions.TransactionType;

/**
 * Proves that one user cannot reach another user's data.
 *
 * <p>Role checks and ownership checks answer different questions. A role says
 * what kind of account you are; ownership says which rows you may touch.
 * Enforcing only roles leaves every authenticated user able to read every other
 * user's financial records, which is the more damaging failure of the two.
 *
 * <p>The expected status throughout is <strong>404, not 403</strong>. A 403
 * confirms that a resource with that id exists, letting an attacker walk the id
 * space and learn which ones are real. From outside, "does not exist" and "is
 * not yours" must be indistinguishable.
 */
class ResourceOwnershipIT extends AbstractIntegrationTest {

    private String alice;
    private String bob;
    private Long alicesProperty;
    private Long alicesTransaction;

    @BeforeEach
    void setUpTwoUsers() throws Exception {
        resetDatabase();

        alice = registerAndSignIn("alice");
        bob = registerAndSignIn("bob");

        alicesProperty = createProperty(alice, "Alice's Bungalow");
        alicesTransaction = createTransaction(alice, alicesProperty, "Alice's repair");
    }

    private Long createProperty(String auth, String name) throws Exception {
        String body = objectMapper.writeValueAsString(new PropertyRequest(
                name, "123 Somewhere", null, new BigDecimal("100.00"),
                2, 1, 1, true, null, null, null));

        String created = mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(created).get("id").asLong();
    }

    private Long createTransaction(String auth, Long propertyId, String description)
            throws Exception {
        String body = objectMapper.writeValueAsString(new TransactionRequest(
                propertyId, TransactionType.EXPENSE, TransactionCategory.MAINTENANCE,
                null, description, new BigDecimal("50.00"), new Date(),
                null, null, null, false, null, null, null, null, null, null, null, null,
                List.of(), Map.of()));

        String created = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(created).get("id").asLong();
    }

    private PropertyRequest anyPropertyRequest() {
        return new PropertyRequest("Hijacked", "Elsewhere", null, new BigDecimal("1.00"),
                1, 1, 1, true, null, null, null);
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("properties")
    class Properties {

        @Test
        @DisplayName("Bob cannot read Alice's property")
        void cannotReadAnotherUsersProperty() throws Exception {
            mockMvc.perform(get("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Alice can read her own property")
        void ownerCanReadOwnProperty() throws Exception {
            mockMvc.perform(get("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alice's Bungalow"));
        }

        @Test
        @DisplayName("Bob's listing does not include Alice's property")
        void listingIsScopedToOwner() throws Exception {
            createProperty(bob, "Bob's Condo");

            mockMvc.perform(get("/api/properties").header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Bob's Condo"));
        }

        @Test
        @DisplayName("Bob cannot update Alice's property")
        void cannotUpdateAnotherUsersProperty() throws Exception {
            mockMvc.perform(put("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, bob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(anyPropertyRequest())))
                    .andExpect(status().isNotFound());

            // And the property is untouched.
            mockMvc.perform(get("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(jsonPath("$.name").value("Alice's Bungalow"));
        }

        @Test
        @DisplayName("Bob cannot delete Alice's property")
        void cannotDeleteAnotherUsersProperty() throws Exception {
            mockMvc.perform(delete("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a property cannot be transferred to another account by update")
        void updateCannotReassignOwnership() throws Exception {
            String created = mockMvc.perform(get("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andReturn().getResponse().getContentAsString();
            long ownerBefore = objectMapper.readTree(created).get("ownerId").asLong();

            mockMvc.perform(put("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, alice)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(anyPropertyRequest())))
                    .andExpect(status().isOk())
                    // PropertyRequest has no owner field, so there is nothing to
                    // bind -- a stronger guarantee than accepting and ignoring it.
                    .andExpect(jsonPath("$.ownerId").value(ownerBefore));
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("transactions")
    class Transactions {

        @Test
        @DisplayName("Bob cannot read Alice's transaction")
        void cannotReadAnotherUsersTransaction() throws Exception {
            mockMvc.perform(get("/api/transactions/{id}", alicesTransaction)
                            .header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Bob's listing does not include Alice's transactions")
        void listingIsScopedToOwner() throws Exception {
            mockMvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));

            mockMvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("search is scoped to the caller and the scope cannot be overridden")
        void searchIsScopedToOwner() throws Exception {
            // An unfiltered search is the most likely way for a scoping bug to
            // leak everything at once.
            var everything = new TransactionSearchRequest(null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);

            String body = mockMvc.perform(post("/api/transactions/search")
                            .header(HttpHeaders.AUTHORIZATION, bob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(everything)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("Bob cannot list transactions for Alice's property")
        void cannotListTransactionsForAnotherUsersProperty() throws Exception {
            // The property id is a path variable, so this is the obvious probe:
            // guess an id and read someone else's books.
            mockMvc.perform(get("/api/transactions/property/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Bob cannot file a transaction against Alice's property")
        void cannotWriteAgainstAnotherUsersProperty() throws Exception {
            // Writing into another account's books is worse than reading them,
            // and it is a distinct check: the referenced property must be
            // resolved through an owner-scoped lookup, not findById.
            String body = objectMapper.writeValueAsString(new TransactionRequest(
                    alicesProperty, TransactionType.EXPENSE, TransactionCategory.CLEANING,
                    null, "Bob's intrusion", new BigDecimal("10.00"), new Date(),
                    null, null, null, false, null, null, null, null, null, null, null, null,
                    List.of(), Map.of()));

            mockMvc.perform(post("/api/transactions")
                            .header(HttpHeaders.AUTHORIZATION, bob)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Bob cannot update or delete Alice's transaction")
        void cannotMutateAnotherUsersTransaction() throws Exception {
            String body = objectMapper.writeValueAsString(new TransactionRequest(
                    alicesProperty, TransactionType.EXPENSE, TransactionCategory.CLEANING,
                    null, "Tampered", new BigDecimal("1.00"), new Date(),
                    null, null, null, false, null, null, null, null, null, null, null, null,
                    List.of(), Map.of()));

            mockMvc.perform(put("/api/transactions/{id}", alicesTransaction)
                            .header(HttpHeaders.AUTHORIZATION, bob)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());

            mockMvc.perform(delete("/api/transactions/{id}", alicesTransaction)
                            .header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/transactions/{id}", alicesTransaction)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Alice's repair"));
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("administrators")
    class Administrators {

        @Test
        @DisplayName("an admin can read across owners")
        void adminSeesEverything() throws Exception {
            String admin = registerAdminAndSignIn("root");

            mockMvc.perform(get("/api/properties").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            mockMvc.perform(get("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/transactions/{id}", alicesTransaction)
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk());
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("referential integrity")
    class ReferentialIntegrity {

        @Test
        @DisplayName("deleting a property that has transactions returns 409")
        void cannotDeletePropertyWithTransactions() throws Exception {
            // ON DELETE RESTRICT surfaces through the API rather than silently
            // orphaning financial records.
            mockMvc.perform(delete("/api/properties/{id}", alicesProperty)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflict"));
        }

        @Test
        @DisplayName("a property with no transactions can be deleted")
        void canDeletePropertyWithoutTransactions() throws Exception {
            Long spare = createProperty(alice, "Spare");

            mockMvc.perform(delete("/api/properties/{id}", spare)
                            .header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isNoContent());
        }
    }
}
