package com.hoseacodes.propflow.repository;

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
 *
 * <p>There are no derived finders here on purpose. Every read is
 * ownership-scoped, and expressing that as a {@code Specification} composed into
 * each query means the scope is applied in one place instead of being
 * re-remembered per method.
 */
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
}
