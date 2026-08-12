package com.hoseacodes.propflow.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoseacodes.propflow.dto.request.TransactionRequest;
import com.hoseacodes.propflow.dto.request.TransactionSearchRequest;
import com.hoseacodes.propflow.dto.response.PagedResponse;
import com.hoseacodes.propflow.dto.response.TransactionResponse;
import com.hoseacodes.propflow.dto.response.TransactionSummaryResponse;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.service.TransactionService;

import jakarta.validation.Valid;

/**
 * Transaction endpoints.
 *
 * <p>Every read is scoped to the authenticated caller in the service layer, so
 * a transaction belonging to another account is reported as 404 rather than
 * 403 -- 403 would confirm the id exists and let an attacker enumerate.
 *
 * <h2>A removed endpoint</h2>
 * {@code GET /api/transactions/user/{userId}} is gone. Once every listing is
 * scoped to the caller, it is redundant for its own data and an
 * insecure-direct-object-reference for anyone else's: the user id came from the
 * path, so it was an invitation to read another account's financial records by
 * changing a number. Administrators can filter by owner through search.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TransactionSummaryResponse>> getAll(
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(transactionService.getAll(principal, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(transactionService.getById(id, principal));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<PagedResponse<TransactionSummaryResponse>> getByPropertyId(
            @PathVariable Long propertyId,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(
                transactionService.getByPropertyId(propertyId, principal, pageable));
    }

    /**
     * Records a transaction against the authenticated user.
     *
     * <p>The owner comes from the verified token, and the referenced property is
     * resolved through an owner-scoped lookup -- so a caller can neither file a
     * transaction into someone else's books nor attach one to a property they do
     * not own.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal User principal) {

        TransactionResponse created = transactionService.create(request, principal);

        return ResponseEntity
                .created(URI.create("/api/transactions/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(transactionService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User principal) {
        transactionService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    /**
     * Filtered search, scoped to the caller.
     *
     * <p>POST is used for a read, which is a deliberate exception to REST
     * convention rather than an oversight. The filter has sixteen optional
     * criteria including free text and date ranges; encoded as query parameters
     * that is unwieldy and risks exceeding URL length limits in proxies. The
     * cost is that responses are not cacheable and the call is not bookmarkable
     * -- acceptable for an authenticated, highly variable report query.
     */
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<TransactionSummaryResponse>> search(
            @Valid @RequestBody TransactionSearchRequest request,
            @AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(transactionService.search(request, principal));
    }
}
