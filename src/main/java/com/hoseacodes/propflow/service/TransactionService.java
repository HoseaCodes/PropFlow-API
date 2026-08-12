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
import com.hoseacodes.propflow.model.Role;
import com.hoseacodes.propflow.model.User;
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

    /**
     * The ownership predicate applied to every read.
     *
     * <p>Returns {@code null} for an administrator, and {@code Specification.allOf}
     * skips nulls -- so the admin bypass exists in exactly one place instead of
     * being re-expressed as an {@code if} at every call site.
     */
    private static Specification<Transaction> scopedTo(User caller) {
        return isAdmin(caller) ? null : TransactionSpecifications.ownedBy(caller.getId());
    }

    private static boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public PagedResponse<TransactionSummaryResponse> getAll(User caller, Pageable pageable) {
        Page<Transaction> page = transactionRepository.findAll(
                Specification.allOf(scopedTo(caller)), pageable);
        return PagedResponse.from(page, TransactionSummaryResponse::from);
    }

    public TransactionResponse getById(Long id, User caller) {
        // The ownership predicate is part of the lookup, so another user's
        // transaction is simply not found -- indistinguishable from one that
        // does not exist.
        Transaction transaction = transactionRepository.findOne(Specification.allOf(
                        scopedTo(caller), TransactionSpecifications.hasId(id)))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        return TransactionResponse.from(transaction);
    }

    public PagedResponse<TransactionSummaryResponse> getByPropertyId(Long propertyId,
                                                                     User caller,
                                                                     Pageable pageable) {
        Page<Transaction> page = transactionRepository.findAll(Specification.allOf(
                scopedTo(caller), TransactionSpecifications.forProperty(propertyId)), pageable);
        return PagedResponse.from(page, TransactionSummaryResponse::from);
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
    public PagedResponse<TransactionSummaryResponse> search(TransactionSearchRequest request,
                                                            User caller) {
        // The ownership scope is composed in first, from the principal. It is
        // deliberately absent from TransactionSearchRequest: a scope a client
        // can supply is a filter, and a filter can be omitted.
        Specification<Transaction> spec = Specification.allOf(
                scopedTo(caller),
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
    public TransactionResponse create(TransactionRequest request, User caller) {
        Transaction transaction = new Transaction();
        transaction.setUser(caller);
        applyRequest(request, transaction, caller);
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
    public TransactionResponse update(Long id, TransactionRequest request, User caller) {
        Transaction transaction = load(id, caller);
        applyRequest(request, transaction, caller);
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long id, User caller) {
        // Loaded through the scoped query rather than existsById-then-delete:
        // that sequence is a check-then-act race, and it ignored ownership.
        transactionRepository.delete(load(id, caller));
    }

    // ------------------------------------------------------------------

    private Transaction load(Long id, User caller) {
        return transactionRepository.findOne(Specification.allOf(
                        scopedTo(caller), TransactionSpecifications.hasId(id)))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }

    /**
     * Copies request fields onto an entity and enforces the domain rules that
     * span more than one field.
     */
    private void applyRequest(TransactionRequest request, Transaction transaction, User caller) {
        // The rule TransactionCategory.isValidForType has always encoded but
        // that nothing ever called: an INCOME transaction categorised as
        // MORTGAGE would inflate reported revenue, and every field is
        // individually valid, so no field annotation can catch it.
        if (!TransactionCategory.isValidForType(request.type(), request.category())) {
            throw new BusinessRuleViolationException(
                    "Category %s is not valid for a %s transaction"
                            .formatted(request.category(), request.type()));
        }

        // Resolved through the OWNER-SCOPED lookup. Without this, an
        // authenticated user could file transactions against someone else's
        // property -- writing into another account's books even though they
        // could not read them.
        Property property = (isAdmin(caller)
                ? propertyRepository.findById(request.propertyId())
                : propertyRepository.findByIdAndOwner(request.propertyId(), caller))
                .orElseThrow(() -> new ResourceNotFoundException("Property", request.propertyId()));

        transaction.setProperty(property);
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
