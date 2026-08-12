package com.hoseacodes.propflow.dto.response;

import java.math.BigDecimal;

import com.hoseacodes.propflow.model.Property;

/**
 * The public representation of a property.
 *
 * <p>Separate from the entity so the database schema stops being the API
 * contract. With the entity returned directly, renaming a column was a breaking
 * change for every client, and adding an internal field published it. Mapping
 * explicitly here means a new column reaches the API only when someone decides
 * it should.
 */
public record PropertyResponse(
        Long id,
        Long ownerId,
        String name,
        String address,
        String description,
        BigDecimal basePrice,
        Integer maxGuests,
        Integer bedrooms,
        Integer bathrooms,
        Boolean active,
        String strPermitNumber,
        String houseRules,
        String checkInInstructions
) {

    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                // Identifier only -- reading it does not initialise the lazy
                // proxy, and the owner's email and name are not this
                // resource's business.
                property.getOwner().getId(),
                property.getName(),
                property.getAddress(),
                property.getDescription(),
                property.getBasePrice(),
                property.getMaxGuests(),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getActive(),
                property.getStrPermitNumber(),
                property.getHouseRules(),
                property.getCheckInInstructions());
    }
}
