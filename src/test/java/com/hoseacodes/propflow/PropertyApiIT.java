package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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

    @BeforeEach
    void resetProperties() {
        propertyRepository.deleteAll();
    }

    private static Property sampleProperty() {
        Property property = new Property();
        property.setName("Bishop Arts Bungalow");
        property.setAddress("123 N Bishop Ave, Dallas, TX");
        property.setDescription("Two-bedroom bungalow walking distance to Bishop Arts.");
        property.setBasePrice(new BigDecimal("189.50"));
        property.setMaxGuests(4);
        property.setBedrooms(2);
        property.setBathrooms(1);
        property.setActive(true);
        property.setStrPermitNumber("STR-2024-0142");
        return property;
    }

    @Test
    @DisplayName("a created property is persisted and readable by id")
    void createThenReadProperty() throws Exception {
        String created = mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProperty())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Bishop Arts Bungalow"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.strPermitNumber").value("STR-2024-0142"));

        // Confirm it really reached PostgreSQL rather than only round-tripping
        // through the persistence context.
        assertThat(propertyRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("BigDecimal prices survive the round trip without precision loss")
    void basePricePrecisionIsPreserved() throws Exception {
        Property property = sampleProperty();
        property.setBasePrice(new BigDecimal("1234.56"));

        String created = mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(property)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        // NUMERIC(19,2) in the migration; compareTo rather than equals so that
        // a scale difference (1234.56 vs 1234.5600) is not treated as a change
        // in value.
        assertThat(propertyRepository.findById(id))
                .get()
                .extracting(Property::getBasePrice)
                .satisfies(price -> assertThat(price).usingComparator(BigDecimal::compareTo)
                        .isEqualTo(new BigDecimal("1234.56")));
    }

    @Test
    @DisplayName("listing returns every persisted property")
    void listProperties() throws Exception {
        propertyRepository.save(sampleProperty());
        propertyRepository.save(sampleProperty());

        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("an update changes the stored property")
    void updateProperty() throws Exception {
        Long id = propertyRepository.save(sampleProperty()).getId();

        Property update = sampleProperty();
        update.setName("Bishop Arts Bungalow - Renovated");
        update.setMaxGuests(6);

        mockMvc.perform(put("/api/properties/{id}", id)
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
    @DisplayName("a deleted property is removed from the database")
    void deleteProperty() throws Exception {
        Long id = propertyRepository.save(sampleProperty()).getId();

        mockMvc.perform(delete("/api/properties/{id}", id))
                .andExpect(status().isOk());

        assertThat(propertyRepository.findById(id)).isEmpty();
    }
}
