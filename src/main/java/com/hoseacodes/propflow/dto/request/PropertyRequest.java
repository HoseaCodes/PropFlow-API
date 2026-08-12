package com.hoseacodes.propflow.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a property.
 *
 * <p>Used for both POST and PUT: every mutable field is present, so a PUT
 * replaces the resource in full, which is what PUT means. A partial-update
 * endpoint would be a PATCH with a separate, all-optional type.
 *
 * <p>Note what is absent. There is no {@code id} -- that comes from the path,
 * so a body claiming a different id cannot silently retarget the write. There
 * is no {@code owner} -- ownership is derived from the authenticated principal,
 * never from client input.
 *
 * <p>{@code active} is a boxed {@link Boolean} rather than a primitive on
 * purpose: a primitive would default to {@code false} when the field is
 * omitted, quietly deactivating a property. As a boxed type it is null when
 * absent, and {@code @NotNull} turns that into an explicit 400.
 */
public record PropertyRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "address is required")
        @Size(max = 255, message = "address must be at most 255 characters")
        String address,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        // Digits matches the NUMERIC(19,2) column: rejecting an over-precise
        // value with a 400 is better than letting the database silently round
        // it, because a silently rounded price is a wrong price.
        @NotNull(message = "basePrice is required")
        @PositiveOrZero(message = "basePrice must not be negative")
        @DecimalMax(value = "99999999.99", message = "basePrice is unreasonably large")
        @Digits(integer = 17, fraction = 2,
                message = "basePrice must have at most 2 decimal places")
        BigDecimal basePrice,

        @NotNull(message = "maxGuests is required")
        @Min(value = 1, message = "maxGuests must be at least 1")
        @Max(value = 100, message = "maxGuests must be at most 100")
        Integer maxGuests,

        @NotNull(message = "bedrooms is required")
        @Min(value = 0, message = "bedrooms must not be negative")
        @Max(value = 50, message = "bedrooms must be at most 50")
        Integer bedrooms,

        @NotNull(message = "bathrooms is required")
        @Min(value = 0, message = "bathrooms must not be negative")
        @Max(value = 50, message = "bathrooms must be at most 50")
        Integer bathrooms,

        @NotNull(message = "active is required")
        Boolean active,

        @Size(max = 255, message = "strPermitNumber must be at most 255 characters")
        String strPermitNumber,

        String houseRules,

        String checkInInstructions
) {
}
