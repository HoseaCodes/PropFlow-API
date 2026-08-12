package com.hoseacodes.propflow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.hoseacodes.propflow.model.transactions.Transaction;

/**
 * Transaction persistence.
 *
 * <p>{@link JpaSpecificationExecutor} is what makes the dynamic search work.
 * Without it the {@code findAll(Specification, Pageable)} overload does not
 * exist, so the service's call resolved to {@code findAll(Pageable)} instead --
 * it compiled, returned a well-formed page, and silently ignored every filter.
 * The bug was invisible because the response looked correct.
 *
 * <p>The narrow finders that were here previously ({@code findByType},
 * {@code findByCategory}, {@code findByDateRange}, {@code findByAmountRange})
 * are gone. None was called by anything, and each is a special case of the
 * composable specifications in {@code TransactionSpecifications}.
 */
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Page<Transaction> findByUserId(String userId, Pageable pageable);

    Page<Transaction> findByPropertyId(Long propertyId, Pageable pageable);
}
