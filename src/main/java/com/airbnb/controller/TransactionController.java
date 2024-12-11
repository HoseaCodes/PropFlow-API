package com.airbnb.controller;

import com.airbnb.model.transactions.Transaction;
import com.airbnb.model.transactions.TransactionFilter;
import com.airbnb.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = {"https://prop-flow-ui.vercel.app", "http://localhost:4200"})
public class TransactionController {
    
    @Autowired
    private TransactionService service;
    
    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(service.getAllTransactions());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        return service.getTransactionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Transaction>> getTransactionsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(service.getTransactionsByUserId(userId));
    }
    
    @GetMapping("/property/{propertyId}")
    public ResponseEntity<List<Transaction>> getTransactionsByPropertyId(@PathVariable Long propertyId) {
        return ResponseEntity.ok(service.getTransactionsByPropertyId(propertyId));
    }
    
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return ResponseEntity.ok(service.createTransaction(transaction));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(
            @PathVariable Long id,
            @RequestBody Transaction transaction) {
        return ResponseEntity.ok(service.updateTransaction(id, transaction));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        service.deleteTransaction(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/search")
    public ResponseEntity<Page<Transaction>> searchTransactions(@RequestBody TransactionFilter filter) {
        return ResponseEntity.ok(service.searchTransactions(filter));
    }
}