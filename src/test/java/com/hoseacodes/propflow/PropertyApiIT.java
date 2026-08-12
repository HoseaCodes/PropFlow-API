package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.hoseacodes.propflow.dto.request.PropertyRequest;
import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.repository.PropertyRepository;

/**
 * Exercises the property endpoints end to end: HTTP request -> controller ->
 * service -> repository -> PostgreSQL, and back out as JSON.
 *
 * <p>Nothing is mocked. The value of these tests is that they would catch a
 * defect anywhere along that path -- a broken mapping, a migration that omits a
 * column, a JSON serialisation change, a transaction that never commits. A
 * service test with a mocked repository proves only that the service calls the
 * mock.
 */
class PropertyApiIT extends AbstractIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;

    /** Every property endpoint requires a bearer token. */
    private String auth;

    @BeforeEach
    void resetProperties() throws Exception {
        resetDatabase();
        auth = registerAndSignIn("property-owner-" + System.nanoTime());
    }

    private static PropertyRequest sampleRequest() {
        return new PropertyRequest(
                "Bishop Arts Bungalow",
                "123 N Bishop Ave, Dallas, TX",
                "Two-bedroom bungalow walking distance to Bishop Arts.",
                new BigDecimal("189.50"),
                4, 2, 1, true,
                "STR-2024-0142",
                "No smoking. Quiet hours after 10pm.",
                "Keypad code sent the morning of check-in.");
    }

    private Long createProperty(PropertyRequest request) throws Exception {
        String created = mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("returns 201 with a Location header and persists the property")
        void createsProperty() throws Exception {
            mockMvc.perform(post("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                            "/api/properties/")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Bishop Arts Bungalow"))
                    .andExpect(jsonPath("$.strPermitNumber").value("STR-2024-0142"));

            assertThat(propertyRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("rejects a missing required field with 400 and names it")
        void rejectsMissingFields() throws Exception {
            mockMvc.perform(post("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.name").exists())
                    .andExpect(jsonPath("$.errors.address").exists())
                    .andExpect(jsonPath("$.errors.basePrice").exists())
                    .andExpect(jsonPath("$.errors.maxGuests").exists());

            assertThat(propertyRepository.count()).isZero();
        }

        @Test
        @DisplayName("rejects a negative price")
        void rejectsNegativePrice() throws Exception {
            PropertyRequest request = new PropertyRequest(
                    "Bad", "Somewhere", null, new BigDecimal("-1.00"),
                    2, 1, 1, true, null, null, null);

            mockMvc.perform(post("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.basePrice").exists());
        }

        @Test
        @DisplayName("rejects a price with more precision than the column stores")
        void rejectsOverPrecisePrice() throws Exception {
            // Better a 400 than a silent round: a silently rounded price is a
            // wrong price, and the client never learns it was changed.
            PropertyRequest request = new PropertyRequest(
                    "Too precise", "Somewhere", null, new BigDecimal("100.12345"),
                    2, 1, 1, true, null, null, null);

            mockMvc.perform(post("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.basePrice").exists());
        }

        @Test
        @DisplayName("rejects zero guests")
        void rejectsZeroGuests() throws Exception {
            PropertyRequest request = new PropertyRequest(
                    "Nobody", "Somewhere", null, new BigDecimal("10.00"),
                    0, 1, 1, true, null, null, null);

            mockMvc.perform(post("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.maxGuests").exists());
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("returns a property by id")
        void readsById() throws Exception {
            Long id = createProperty(sampleRequest());

            mockMvc.perform(get("/api/properties/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.name").value("Bishop Arts Bungalow"));
        }

        @Test
        @DisplayName("returns 404 for an unknown id")
        void unknownIdIsNotFound() throws Exception {
            // Previously a bare RuntimeException, which surfaced as 500 --
            // reporting a client mistake as a server fault.
            mockMvc.perform(get("/api/properties/{id}", 999999)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Resource not found"));
        }

        @Test
        @DisplayName("returns 400 for a non-numeric id")
        void nonNumericIdIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/properties/{id}", "not-a-number")
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("BigDecimal prices survive the round trip without precision loss")
        void pricePrecisionIsPreserved() throws Exception {
            PropertyRequest request = new PropertyRequest(
                    "Precise", "Somewhere", null, new BigDecimal("1234.56"),
                    2, 1, 1, true, null, null, null);

            Long id = createProperty(request);

            // compareTo rather than equals so a scale difference
            // (1234.56 vs 1234.5600) is not mistaken for a change in value.
            assertThat(propertyRepository.findById(id))
                    .get()
                    .extracting(Property::getBasePrice)
                    .satisfies(price -> assertThat(price)
                            .usingComparator(BigDecimal::compareTo)
                            .isEqualTo(new BigDecimal("1234.56")));
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("pagination")
    class Pagination {

        @Test
        @DisplayName("returns a paged envelope with totals")
        void returnsPagedEnvelope() throws Exception {
            for (int i = 0; i < 3; i++) {
                createProperty(sampleRequest());
            }

            mockMvc.perform(get("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .param("page", "0").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.last").value(false));
        }

        @Test
        @DisplayName("marks the final page as last")
        void marksLastPage() throws Exception {
            createProperty(sampleRequest());

            mockMvc.perform(get("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.last").value(true))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("caps an oversized page size rather than returning everything")
        void capsPageSize() throws Exception {
            createProperty(sampleRequest());

            // Without max-page-size a client can ask for the entire table and
            // undo the point of paginating.
            mockMvc.perform(get("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .param("size", "100000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("update and delete")
    class Mutate {

        @Test
        @DisplayName("an update changes the stored property")
        void updatesProperty() throws Exception {
            Long id = createProperty(sampleRequest());

            PropertyRequest update = new PropertyRequest(
                    "Bishop Arts Bungalow - Renovated",
                    "123 N Bishop Ave, Dallas, TX", "Now with a hot tub.",
                    new BigDecimal("225.00"), 6, 3, 2, true,
                    "STR-2024-0142", "No smoking.", "Keypad code.");

            mockMvc.perform(put("/api/properties/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Bishop Arts Bungalow - Renovated"))
                    .andExpect(jsonPath("$.maxGuests").value(6));

            assertThat(propertyRepository.findById(id))
                    .get()
                    .extracting(Property::getMaxGuests)
                    .isEqualTo(6);
        }

        @Test
        @DisplayName("updating an unknown property returns 404")
        void updateUnknownIsNotFound() throws Exception {
            mockMvc.perform(put("/api/properties/{id}", 999999)
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("an invalid update is rejected and leaves the property unchanged")
        void invalidUpdateDoesNotMutate() throws Exception {
            Long id = createProperty(sampleRequest());

            mockMvc.perform(put("/api/properties/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest());

            assertThat(propertyRepository.findById(id))
                    .get()
                    .extracting(Property::getName)
                    .isEqualTo("Bishop Arts Bungalow");
        }

        @Test
        @DisplayName("delete returns 204 and removes the property")
        void deletesProperty() throws Exception {
            Long id = createProperty(sampleRequest());

            mockMvc.perform(delete("/api/properties/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isNoContent());

            assertThat(propertyRepository.findById(id)).isEmpty();
        }

        @Test
        @DisplayName("deleting an unknown property returns 404")
        void deleteUnknownIsNotFound() throws Exception {
            mockMvc.perform(delete("/api/properties/{id}", 999999)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isNotFound());
        }
    }
}
