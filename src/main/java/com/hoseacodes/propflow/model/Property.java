package com.hoseacodes.propflow.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A short-term rental property.
 *
 * <p>Annotated {@code @Getter @Setter} rather than Lombok's {@code @Data},
 * which also generates {@code equals}, {@code hashCode}, and {@code toString}
 * across every field. All three are hazards on a JPA entity:
 * <ul>
 *   <li>{@code equals}/{@code hashCode} over mutable fields break the contract
 *       as soon as an entity is mutated while in a {@code HashSet}, and a
 *       generated id is null before persist, so a transient and a persisted
 *       instance of the same object compare unequal.</li>
 *   <li>{@code toString} touches every field, including lazy associations,
 *       which triggers loading -- or a {@code LazyInitializationException}
 *       outside a transaction -- from something as innocuous as a log line.</li>
 * </ul>
 * Identity-based equality (the default from {@code Object}) is the safe choice
 * for entities managed within a persistence context.
 */
@Getter
@Setter
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String address;

    private String description;

    /**
     * Mapped to NUMERIC(19,2).
     *
     * <p>{@code BigDecimal}, never {@code double}: IEEE-754 binary floating
     * point cannot represent most decimal fractions exactly, so 0.1 + 0.2 is
     * 0.30000000000000004 and a column of summed prices will not reconcile.
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal basePrice;

    private Integer maxGuests;

    private Integer bedrooms;

    private Integer bathrooms;

    private Boolean active;

    private String strPermitNumber;

    @Column(columnDefinition = "TEXT")
    private String houseRules;

    @Column(columnDefinition = "TEXT")
    private String checkInInstructions;

    /**
     * Optimistic locking counter.
     *
     * <p>Two concurrent updates to the same property would otherwise both
     * succeed, with the later write silently discarding the earlier one. With a
     * version column, the second flush fails with an
     * {@code OptimisticLockingFailureException}, which the exception handler
     * turns into a 409 telling the client to re-read and retry.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
