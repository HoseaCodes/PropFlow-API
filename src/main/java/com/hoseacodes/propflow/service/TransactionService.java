package com.hoseacodes.propflow.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoseacodes.propflow.dto.request.TransactionRequest;
import com.hoseacodes.propflow.dto.request.TransactionSearchRequest;
import com.hoseacodes.propflow.dto.response.PagedResponse;
import com.hoseacodes.propflow.dto.response.TransactionResponse;
import com.hoseacodes.propflow.dto.response.TransactionSummaryResponse;
import com.hoseacodes.propflow.exception.BusinessRuleViolationException;
import com.hoseacodes.propflow.exception.ResourceNotFoundException;
import com.hoseacodes.propflow.model.Property;
import com.hoseacodes.propflow.model.transactions.Transaction;
import com.hoseacodes.propflow.model.transactions.TransactionCategory;
import com.hoseacodes.propflow.repository.PropertyRepository;
import com.hoseacodes.propflow.repository.TransactionRepository;
import com.hoseacodes.propflow.repository.TransactionSpecifications;

/**
 * Transaction operations.
 *
 * <h2>Why this service returns DTOs</h2>
 * With {@code spring.jpa.open-in-view=false} the persistence context closes
 * when the service method returns, so a lazy collection cannot be traversed by
 * the controller. Mapping here, inside the transaction, makes that boundary
 * explicit: everything the response needs is materialised before the context
 * closes. The alternative -- returning entities and mapping in the controller
 * -- fails at runtime with a {@code LazyInitializationException} the moment
 * anyone touches {@code tags}.
 *
 * <p>The cost is that the service knows about response types. That is a real
 * coupling, accepted because the alternative is a class of runtime error that
 * only appears once a lazy field is added.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PropertyRepository propertyRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              PropertyRepository propertyRepository) {
        this.transactionRepository = transactionRepository;
        this.propertyRepository = propertyRepository;
    }

    public PagedResponse<TransactionSummaryResponse> getAll(Pageable pageable) {
        return PagedResponse.from(
                transactionRepository.findAll(pageable), TransactionSummaryResponse::from);
    }

    public TransactionResponse getById(Long id) {
        return TransactionResponse.from(load(id));
    }

    public PagedResponse<TransactionSummaryResponse> getByUserId(String userId, Pageable pageable) {
        return PagedResponse.from(
                transactionRepository.findByUserId(userId, pageable),
                TransactionSummaryResponse::from);
    }

    public PagedResponse<TransactionSummaryResponse> getByPropertyId(Long propertyId,
                                                                     Pageable pageable) {
        return PagedResponse.from(
                transactionRepository.findByPropertyId(propertyId, pageable),
                TransactionSummaryResponse::from);
    }

    /**
     * Dynamic filtered search.
     *
     * <p>The previous implementation built a full {@code Specification} and then
     * called {@code repository.findAll(pageable)}, discarding it -- because the
     * repository did not extend {@code JpaSpecificationExecutor}, so the
     * two-argument overload did not exist and a different valid overload was
     * selected instead. It compiled, returned a well-formed page, and ignored
     * every filter the caller sent.
     */
    public PagedResponse<TransactionSummaryResponse> search(TransactionSearchRequest request) {
        // NOTE: no owner predicate yet. Search currently spans every user's
        // transactions, because no ownership edge exists in the schema. The
        // filter is deliberately NOT exposed on the request type -- scoping
        // must come from the authenticated principal, never from client input,
        // or it is a filter rather than a control. Added with the ownership
        // migration.
        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecifications.forProperty(request.propertyId()),
                TransactionSpecifications.dateFrom(request.startDate()),
                TransactionSpecifications.dateTo(request.endDate()),
                TransactionSpecifications.amountAtLeast(request.minAmount()),
                TransactionSpecifications.amountAtMost(request.maxAmount()),
                TransactionSpecifications.ofType(request.type()),
                TransactionSpecifications.inCategory(request.category()),
                TransactionSpecifications.withStatus(request.status()),
                TransactionSpecifications.paidWith(request.paymentMethod()),
                TransactionSpecifications.isRecurring(request.recurring()),
                TransactionSpecifications.withFrequency(request.frequency()),
                TransactionSpecifications.vendorContains(request.vendor()),
                TransactionSpecifications.withApprovalStatus(request.approvalStatus()),
                TransactionSpecifications.overdue(request.overdue()),
                TransactionSpecifications.matchesText(request.searchTerm()));

        Page<Transaction> page = transactionRepository.findAll(spec, pageableFor(request));
        return PagedResponse.from(page, TransactionSummaryResponse::from);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request, String userId) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        applyRequest(request, transaction);
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    /**
     * Updates a transaction in place.
     *
     * <p>The previous implementation set the id on a client-supplied object and
     * called {@code save()}, replacing the whole row: every field the client
     * omitted became null, silently wiping {@code approvedBy},
     * {@code refund}, {@code tags}, and the rest. It also used
     * {@code existsById} followed by {@code save}, a check-then-act race.
     *
     * <p>Loading the managed entity and mutating it means only the fields the
     * request carries are written, and the optimistic-lock version guards
     * against a concurrent writer.
     */
    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = load(id);
        applyRequest(request, transaction);
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Transaction", id);
        }
        transactionRepository.deleteById(id);
    }

    // ------------------------------------------------------------------

    private Transaction load(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }

    /**
     * Copies request fields onto an entity and enforces the domain rules that
     * span more than one field.
     */
    private void applyRequest(TransactionRequest request, Transaction transaction) {
        // The rule TransactionCategory.isValidForType has always encoded but
        // that nothing ever called: an INCOME transaction categorised as
        // MORTGAGE would inflate reported revenue, and every field is
        // individually valid, so no field annotation can catch it.
        if (!TransactionCategory.isValidForType(request.type(), request.category())) {
            throw new BusinessRuleViolationException(
                    "Category %s is not valid for a %s transaction"
                            .formatted(request.category(), request.type()));
        }

        Property property = propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property", request.propertyId()));

        transaction.setPropertyId(property.getId());
        // Resolved from the property, never taken from the request: it is a
        // point-in-time snapshot for the financial record, so a later rename
        // does not rewrite history.
        transaction.setPropertyName(property.getName());

        transaction.setType(request.type());
        transaction.setCategory(request.category());
        transaction.setSubcategory(request.subcategory());
        transaction.setDescription(request.description());
        transaction.setAmount(request.amount());
        transaction.setDate(request.date());
        transaction.setStatus(request.status());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setPaymentReference(request.paymentReference());
        transaction.setRecurring(request.recurring() != null && request.recurring());
        transaction.setFrequency(request.frequency());
        transaction.setVendor(request.vendor());
        transaction.setReceiptUrl(request.receiptUrl());
        transaction.setNotes(request.notes());
        transaction.setDueDate(request.dueDate());
        transaction.setPaidAt(request.paidAt());
        transaction.setBookingReference(request.bookingReference());
        transaction.setBookingId(request.bookingId());

        // Mutated in place rather than reassigned; Hibernate tracks the
        // collection instance it handed out.
        transaction.replaceTags(request.tags() == null ? List.of() : request.tags());
        transaction.replaceMetadata(request.metadata());
    }

    /**
     * Builds the page request, rejecting any sort field not on the whitelist.
     *
     * <p>{@code sortBy} becomes a Criteria attribute path. An unvalidated value
     * produces a 500 from an unknown attribute at best, and at worst lets a
     * caller probe the internal model by observing which names are accepted.
     */
    private static Pageable pageableFor(TransactionSearchRequest request) {
        String field = request.sortBy();
        if (field == null || field.isBlank()) {
            field = TransactionSearchRequest.DEFAULT_SORT_FIELD;
        } else if (!TransactionSearchRequest.SORTABLE_FIELDS.contains(field)) {
            throw new BusinessRuleViolationException(
                    "Cannot sort by '%s'. Allowed fields: %s"
                            .formatted(field, TransactionSearchRequest.SORTABLE_FIELDS
                                    .stream().sorted().toList()));
        }

        Sort sort = Sort.by(request.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, field);
        return PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(), sort);
    }
}
