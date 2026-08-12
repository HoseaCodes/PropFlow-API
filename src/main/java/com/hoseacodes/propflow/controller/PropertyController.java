package com.hoseacodes.propflow.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoseacodes.propflow.dto.request.PropertyRequest;
import com.hoseacodes.propflow.dto.response.PagedResponse;
import com.hoseacodes.propflow.dto.response.PropertyResponse;
import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.service.PropertyService;

import jakarta.validation.Valid;

/**
 * Property endpoints.
 *
 * <p>Thin by design: bind, delegate, map, choose a status code. No business
 * logic and no repository access -- both belong in the service, which is also
 * where the transaction boundary lives.
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * Paginated listing.
     *
     * <p>Previously returned an unbounded {@code List} from {@code findAll()},
     * so both the response size and the memory needed to build it grew linearly
     * with the table. {@code @PageableDefault} caps the default page at 20; the
     * hard ceiling is set by {@code spring.data.web.pageable.max-page-size} so
     * that a client cannot request the whole table with {@code ?size=1000000}.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<PropertyResponse>> getAllProperties(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<Property> page = propertyService.getAllProperties(pageable);
        return ResponseEntity.ok(PagedResponse.from(page, PropertyResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getProperty(@PathVariable Long id) {
        return ResponseEntity.ok(PropertyResponse.from(propertyService.getProperty(id)));
    }

    /**
     * Creates a property.
     *
     * <p>201 with a {@code Location} header, not the previous 200. The status
     * tells a client that a new resource now exists, and the header says where
     * -- which is the difference between a response a client has to interpret
     * and one it can follow.
     */
    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(
            @Valid @RequestBody PropertyRequest request) {

        Property created = propertyService.createProperty(request);
        return ResponseEntity
                .created(URI.create("/api/properties/" + created.getId()))
                .body(PropertyResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable Long id,
            @Valid @RequestBody PropertyRequest request) {

        return ResponseEntity.ok(PropertyResponse.from(propertyService.updateProperty(id, request)));
    }

    /** 204, not the previous 200 with an empty body: there is nothing to return. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.noContent().build();
    }
}
