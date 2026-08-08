package com.hoseacodes.propflow.controller;

import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.service.PropertyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@CrossOrigin(origins = "http://localhost:4200")
public class PropertyController {

    private static final Logger logger = LoggerFactory.getLogger(PropertyController.class);

    @Autowired
    private PropertyService propertyService;
    
    @GetMapping
    public List<Property> getAllProperties() {
        logger.debug("Received request to get all properties");
        return propertyService.getAllProperties();
    }
    
    @GetMapping("/{id}")
    public Property getProperty(@PathVariable Long id) {
        logger.debug("Received request to get property with id: {}", id);
        return propertyService.getProperty(id);
    }
    
    @PostMapping
    public Property createProperty(@RequestBody Property property) {
        logger.debug("Received request to create property: {}", property);
        return propertyService.createProperty(property);
    }
    
    @PutMapping("/{id}")
    public Property updateProperty(@PathVariable Long id, @RequestBody Property property) {
        logger.debug("Received request to update property with id: {}", id);
        return propertyService.updateProperty(id, property);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProperty(@PathVariable Long id) {
        logger.debug("Received request to delete property with id: {}", id);
        propertyService.deleteProperty(id);
        return ResponseEntity.ok().build();
    }
}