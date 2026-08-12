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
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PagedResponse<TransactionSummaryResponse>> getByUserId(
            @PathVariable String userId,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.getByUserId(userId, pageable));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<PagedResponse<TransactionSummaryResponse>> getByPropertyId(
            @PathVariable Long propertyId,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.getByPropertyId(propertyId, pageable));
    }

    /**
     * Records a transaction against the authenticated user.
     *
     * <p>The owner comes from the verified token, not the request body. Taking
     * it from the body would let any caller file a transaction into someone
     * else's books.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal User principal) {

        TransactionResponse created =
                transactionService.create(request, String.valueOf(principal.getId()));

        return ResponseEntity
                .created(URI.create("/api/transactions/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Filtered search.
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
            @Valid @RequestBody TransactionSearchRequest request) {
        return ResponseEntity.ok(transactionService.search(request));
    }
}
