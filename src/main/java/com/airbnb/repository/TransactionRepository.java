package com.airbnb.repository;

import com.airbnb.model.transactions.Transaction;
import com.airbnb.model.transactions.TransactionType;
import com.airbnb.model.transactions.TransactionCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Date;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(String userId);
    List<Transaction> findByPropertyId(Long propertyId);
    List<Transaction> findByUserIdAndPropertyId(String userId, Long propertyId);
    
    @Query("SELECT t FROM Transaction t WHERE t.date BETWEEN :startDate AND :endDate")
    List<Transaction> findByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
    
    @Query("SELECT t FROM Transaction t WHERE t.amount BETWEEN :minAmount AND :maxAmount")
    List<Transaction> findByAmountRange(@Param("minAmount") Double minAmount, @Param("maxAmount") Double maxAmount);
    
    List<Transaction> findByType(TransactionType type);
    List<Transaction> findByCategory(TransactionCategory category);
}