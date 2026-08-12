package com.hoseacodes.propflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoseacodes.propflow.dto.request.PropertyRequest;
import com.hoseacodes.propflow.exception.ResourceNotFoundException;
import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.model.Role;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.repository.PropertyRepository;

/**
 * Property operations, scoped to the authenticated caller.
 *
 * <h2>Authorization is part of the query</h2>
 * Reads use {@code findByIdAndOwner} rather than {@code findById} followed by an
 * ownership check. The difference matters: a check after the fact protects only
 * the call sites that remember it, and forgetting one leaks another user's data.
 * A query that cannot return someone else's row fails safe -- a missing scope
 * shows up as an empty result, not a breach.
 *
 * <h2>Why 404 and not 403 for another user's property</h2>
 * 403 confirms that a resource with that id exists, which turns the endpoint
 * into an enumeration oracle: an attacker walks the id space and learns exactly
 * which ones are real. From outside, "does not exist" and "not yours" must be
 * indistinguishable. 403 is reserved for a caller who is authenticated but
 * lacks the required <em>role</em> -- a fact that reveals nothing about data.
 *
 * <h2>Transaction boundaries</h2>
 * The class default is {@code readOnly = true}; writes override it. The service
 * method is the unit of work: a repository call is too small (a read-then-write
 * would span two transactions and lose a concurrent update), and a controller
 * is the wrong layer (transaction scope would be tied to HTTP).
 */
@Service
@Transactional(readOnly = true)
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /** Administrators see every property; everyone else sees only their own. */
    public Page<Property> getAllProperties(User caller, Pageable pageable) {
        return isAdmin(caller)
                ? propertyRepository.findAll(pageable)
                : propertyRepository.findByOwner(caller, pageable);
    }

    public Property getProperty(Long id, User caller) {
        return (isAdmin(caller)
                ? propertyRepository.findById(id)
                : propertyRepository.findByIdAndOwner(id, caller))
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }

    /**
     * Creates a property owned by the caller.
     *
     * <p>Ownership comes from the verified principal. {@code PropertyRequest}
     * has no owner field at all, so there is nothing for a client to set --
     * which is a stronger guarantee than accepting the field and ignoring it.
     */
    @Transactional
    public Property createProperty(PropertyRequest request, User caller) {
        Property property = new Property();
        property.setOwner(caller);
        apply(request, property);
        return propertyRepository.save(property);
    }

    /**
     * Full replacement of a property's mutable fields.
     *
     * <p>The entity is loaded inside this transaction and mutated, so Hibernate
     * dirty-checking issues the UPDATE at flush. Constructing a detached entity
     * from the request and calling {@code save()} would instead write nulls over
     * every field the request did not carry.
     *
     * <p>Ownership is not reassigned: {@code apply} does not touch it, so a
     * property cannot be transferred to another account through an update.
     */
    @Transactional
    public Property updateProperty(Long id, PropertyRequest request, User caller) {
        Property property = getProperty(id, caller);
        apply(request, property);
        return property;
    }

    @Transactional
    public void deleteProperty(Long id, User caller) {
        // Resolved through the scoped read, so deleting someone else's property
        // is a 404 for the same reason reading it is.
        Property property = getProperty(id, caller);

        // If transactions reference this property, the ON DELETE RESTRICT
        // foreign key rejects the delete and the resulting
        // DataIntegrityViolationException surfaces as 409. That is deliberate:
        // financial history must not disappear as a side effect of removing a
        // property.
        propertyRepository.delete(property);
    }

    private static boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private static void apply(PropertyRequest request, Property property) {
        property.setName(request.name());
        property.setAddress(request.address());
        property.setDescription(request.description());
        property.setBasePrice(request.basePrice());
        property.setMaxGuests(request.maxGuests());
        property.setBedrooms(request.bedrooms());
        property.setBathrooms(request.bathrooms());
        property.setActive(request.active());
        property.setStrPermitNumber(request.strPermitNumber());
        property.setHouseRules(request.houseRules());
        property.setCheckInInstructions(request.checkInInstructions());
    }
}
