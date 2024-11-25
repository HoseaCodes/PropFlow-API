package com.airbnb.service;

import com.airbnb.model.Property;
import com.airbnb.repository.PropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PropertyService {
    @Autowired
    private PropertyRepository propertyRepository;
    
    public List<Property> getAllProperties() {
        return propertyRepository.findAll();
    }
    
    public Property getProperty(Long id) {
        return propertyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Property not found"));
    }
    
    public Property createProperty(Property property) {
        return propertyRepository.save(property);
    }
    
    public Property updateProperty(Long id, Property property) {
        Property existingProperty = getProperty(id);
        // Update properties
        existingProperty.setName(property.getName());
        existingProperty.setAddress(property.getAddress());
        existingProperty.setDescription(property.getDescription());
        existingProperty.setBasePrice(property.getBasePrice());
        existingProperty.setMaxGuests(property.getMaxGuests());
        existingProperty.setBedrooms(property.getBedrooms());
        existingProperty.setBathrooms(property.getBathrooms());
        existingProperty.setActive(property.getActive());
        existingProperty.setStrPermitNumber(property.getStrPermitNumber());
        existingProperty.setHouseRules(property.getHouseRules());
        existingProperty.setCheckInInstructions(property.getCheckInInstructions());
        
        return propertyRepository.save(existingProperty);
    }
    
    public void deleteProperty(Long id) {
        propertyRepository.deleteById(id);
    }
}
