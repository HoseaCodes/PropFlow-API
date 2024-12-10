package com.airbnb.service;

import com.airbnb.model.transactions.Transaction;
import com.airbnb.model.transactions.TransactionFilter;
import com.airbnb.repository.TransactionRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionService {
    
    @Autowired
    private TransactionRepository repository;
    
    public List<Transaction> getAllTransactions() {
        return repository.findAll();
    }
    
    public Optional<Transaction> getTransactionById(Long id) {
        return repository.findById(id);
    }
    
    public List<Transaction> getTransactionsByUserId(String userId) {
        return repository.findByUserId(userId);
    }
    
    public List<Transaction> getTransactionsByPropertyId(Long propertyId) {
        return repository.findByPropertyId(propertyId);
    }
    
    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        validateTransaction(transaction);
        return repository.save(transaction);
    }
    
    @Transactional
    public Transaction updateTransaction(Long id, Transaction transaction) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Transaction not found with id: " + id);
        }
        transaction.setId(id);
        validateTransaction(transaction);
        return repository.save(transaction);
    }
    
    @Transactional
    public void deleteTransaction(Long id) {
        repository.deleteById(id);
    }
    
    public Page<Transaction> searchTransactions(TransactionFilter filter) {
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Date range
            if (filter.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), filter.getStartDate()));
            }
            if (filter.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), filter.getEndDate()));
            }
            
            // Amount range
            if (filter.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.getMinAmount()));
            }
            if (filter.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.getMaxAmount()));
            }
            
            // Basic filters
            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            }
            if (filter.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), filter.getCategory()));
            }
            if (filter.getPropertyId() != null) {
                predicates.add(cb.equal(root.get("propertyId"), filter.getPropertyId()));
            }
            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getPaymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.getPaymentMethod()));
            }
            if (filter.getRecurring() != null) {
                predicates.add(cb.equal(root.get("recurring"), filter.getRecurring()));
            }
            if (filter.getFrequency() != null) {
                predicates.add(cb.equal(root.get("frequency"), filter.getFrequency()));
            }
            if (filter.getVendor() != null && !filter.getVendor().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("vendor")), 
                    "%" + filter.getVendor().toLowerCase() + "%"));
            }
            
            // Advanced filters
            if (filter.getHasTaxDetails() != null && filter.getHasTaxDetails()) {
                predicates.add(cb.isNotNull(root.get("taxDetails")));
            }
            if (filter.getHasWarranty() != null && filter.getHasWarranty()) {
                predicates.add(cb.isNotEmpty(root.get("warranties")));
            }
            if (filter.getHasRefund() != null && filter.getHasRefund()) {
                predicates.add(cb.isNotNull(root.get("refund")));
            }
            if (filter.getApprovalStatus() != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), filter.getApprovalStatus()));
            }
            if (filter.getOverdue() != null && filter.getOverdue()) {
                predicates.add(cb.lessThan(root.get("dueDate"), new Date()));
            }
            if (filter.getNeedsApproval() != null && filter.getNeedsApproval()) {
                predicates.add(cb.equal(root.get("approvalStatus"), "PENDING"));
            }
            
            // Search term (searches across multiple fields)
            if (filter.getSearchTerm() != null && !filter.getSearchTerm().isEmpty()) {
                String searchTerm = "%" + filter.getSearchTerm().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("description")), searchTerm),
                    cb.like(cb.lower(root.get("notes")), searchTerm),
                    cb.like(cb.lower(root.get("vendor")), searchTerm),
                    cb.like(cb.lower(root.get("paymentReference")), searchTerm)
                ));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        // Handle pagination and sorting
        Sort sort = Sort.by(
            filter.getSortDirection() != null && filter.getSortDirection().equalsIgnoreCase("desc") 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC,
            filter.getSortBy() != null ? filter.getSortBy() : "date"
        );
        
        Pageable pageable = PageRequest.of(
            filter.getPage() != null ? filter.getPage() : 0,
            filter.getSize() != null ? filter.getSize() : 20,
            sort
        );
        
        return repository.findAll(pageable);
    }
    
    private void validateTransaction(Transaction transaction) {
        if (transaction.getUserId() == null || transaction.getUserId().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (transaction.getPropertyId() == null) {
            throw new IllegalArgumentException("Property ID is required");
        }
        if (transaction.getAmount() == null || transaction.getAmount() <= 0) {
            throw new IllegalArgumentException("Valid amount is required");
        }
        if (transaction.getDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }
    }
}

