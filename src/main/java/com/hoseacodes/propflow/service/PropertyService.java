package com.hoseacodes.propflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoseacodes.propflow.dto.request.PropertyRequest;
import com.hoseacodes.propflow.exception.ResourceNotFoundException;
import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.repository.PropertyRepository;

/**
 * Property operations.
 *
 * <h2>Transaction boundaries</h2>
 * The class default is {@code readOnly = true}; writes override it. The
 * boundary belongs here rather than in the controller or the repository:
 * <ul>
 *   <li>A repository method is too small a unit -- an operation that reads then
 *       writes would span two transactions, so a concurrent change between them
 *       is lost.</li>
 *   <li>A controller is too large and is the wrong layer -- transaction scope
 *       would become tied to HTTP, and any non-HTTP caller would get none.</li>
 * </ul>
 * The service method is the unit of work: it maps to one business operation
 * that should entirely succeed or entirely fail.
 *
 * <p>{@code readOnly = true} is not merely documentation. It lets Hibernate
 * skip dirty-checking of loaded entities and flags the intent to the JDBC
 * driver, which matters most on a read-heavy reporting workload like this one.
 */
@Service
@Transactional(readOnly = true)
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public Page<Property> getAllProperties(Pageable pageable) {
        return propertyRepository.findAll(pageable);
    }

    public Property getProperty(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }

    @Transactional
    public Property createProperty(PropertyRequest request) {
        Property property = new Property();
        apply(request, property);
        return propertyRepository.save(property);
    }

    /**
     * Full replacement of a property's mutable fields.
     *
     * <p>The entity is loaded inside this transaction and mutated, so Hibernate
     * dirty-checking issues the UPDATE at flush. Note the alternative that is
     * deliberately avoided: constructing a detached entity from the request and
     * calling {@code save()} would write nulls over every field the request did
     * not carry -- which is how the old user update endpoint silently erased
     * data.
     */
    @Transactional
    public Property updateProperty(Long id, PropertyRequest request) {
        Property property = getProperty(id);
        apply(request, property);
        return property;
    }

    @Transactional
    public void deleteProperty(Long id) {
        // Check first so a missing property is a 404 rather than the 500 that
        // deleteById's EmptyResultDataAccessException would have produced.
        if (!propertyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Property", id);
        }
        propertyRepository.deleteById(id);
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
