package com.hoseacodes.propflow.dto.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * A stable envelope for paginated results.
 *
 * <p>Spring's {@code PageImpl} is deliberately not returned directly. Its JSON
 * shape is an accident of its internal fields rather than a designed contract,
 * Spring Boot logs a warning when it is serialised for exactly that reason, and
 * it drags {@code Pageable}/{@code Sort} internals into the response where they
 * become something clients depend on. This record is the contract instead.
 *
 * @param content       the page of items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching rows across all pages
 * @param totalPages    total number of pages
 * @param last          whether this is the final page
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    /** Maps a {@link Page} of entities into a page of response objects. */
    public static <E, T> PagedResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PagedResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isLast());
    }
}
