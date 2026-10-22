// Steps
// Turn optional page and size request parameters into a Pageable sorted by id.
// Reject a missing, negative or oversized parameter with a 400.
// Build the pagination response headers.
package com.orderflow.common;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Similar to offset and limit in SQLAlchemy: Pageable lets Spring Data apply the paging in the database.
// The page data goes in headers so the body stays the same plain array as the unpaginated response.
public final class Pagination {

    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    // Empty means "no pagination requested", so the caller returns the full list as before.
    public static Optional<Pageable> parse(Integer page, Integer size) {
        if (page == null && size == null) {
            return Optional.empty();
        }
        if (page == null || size == null) {
            throw badRequest("Both page and size must be provided");
        }
        if (page < 0) {
            throw badRequest("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw badRequest("size must be between 1 and " + MAX_SIZE);
        }
        // Spring Data stores the row offset in an int, so a larger offset would fail with a 500.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw badRequest("page is too large");
        }
        return Optional.of(PageRequest.of(page, size, Sort.by("id").ascending()));
    }

    public static HttpHeaders headers(Page<?> page) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total-Count", String.valueOf(page.getTotalElements()));
        headers.add("X-Total-Pages", String.valueOf(page.getTotalPages()));
        headers.add("X-Page", String.valueOf(page.getNumber()));
        headers.add("X-Page-Size", String.valueOf(page.getSize()));
        return headers;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
