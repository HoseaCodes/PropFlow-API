package com.airbnb.controller;

import com.airbnb.model.Property;
import com.airbnb.service.PropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@CrossOrigin(origins = "http://localhost:4200")
public class PropertyController {
    @Autowired
    private PropertyService propertyService;
    
    @GetMapping
    public List<Property> getAllProperties() {
        return propertyService.getAllProperties();
    }
    
    @GetMapping("/{id}")
    public Property getProperty(@PathVariable Long id) {
        return propertyService.getProperty(id);
    }
    
    @PostMapping
    public Property createProperty(@RequestBody Property property) {
        return propertyService.createProperty(property);
    }
    
    @PutMapping("/{id}")
    public Property updateProperty(@PathVariable Long id, @RequestBody Property property) {
        return propertyService.updateProperty(id, property);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.ok().build();
    }
}