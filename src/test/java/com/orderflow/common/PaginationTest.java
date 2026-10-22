// Steps
// Check page and size parse into a Pageable sorted by id, and no parameters means no pagination.
// Check a missing, negative, oversized or overflowing parameter is rejected with a 400.
// Check the pagination headers.
package com.orderflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Plain unit tests, like pytest tests for a small helper function.
class PaginationTest {

    @Test
    void noParametersMeansNoPagination() {
        assertThat(Pagination.parse(null, null)).isEmpty();
    }

    @Test
    void validParametersBuildPageableSortedByIdAscending() {
        Optional<Pageable> pageable = Pagination.parse(2, 25);

        assertThat(pageable).isPresent();
        assertThat(pageable.get().getPageNumber()).isEqualTo(2);
        assertThat(pageable.get().getPageSize()).isEqualTo(25);
        assertThat(pageable.get().getSort()).isEqualTo(Sort.by("id").ascending());
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,100", "5,10"})
    void boundaryValuesAreAccepted(int page, int size) {
        assertThat(Pagination.parse(page, size)).isPresent();
    }

    @Test
    void onlyOneParameterIsRejected() {
        assertBadRequest(null, 10, "Both page and size must be provided");
        assertBadRequest(1, null, "Both page and size must be provided");
    }

    @Test
    void negativePageIsRejected() {
        assertBadRequest(-1, 10, "page must be 0 or greater");
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,-5", "0,101", "0,1000"})
    void sizeOutsideOneToHundredIsRejected(int page, int size) {
        assertBadRequest(page, size, "size must be between 1 and 100");
    }

    @Test
    void pageThatWouldOverflowTheRowOffsetIsRejected() {
        assertBadRequest(Integer.MAX_VALUE, 100, "page is too large");
    }

    @Test
    void headersDescribeThePage() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        HttpHeaders headers = Pagination.headers(page);

        assertThat(headers.getFirst("X-Total-Count")).isEqualTo("5");
        assertThat(headers.getFirst("X-Total-Pages")).isEqualTo("3");
        assertThat(headers.getFirst("X-Page")).isEqualTo("1");
        assertThat(headers.getFirst("X-Page-Size")).isEqualTo("2");
    }

    private void assertBadRequest(Integer page, Integer size, String message) {
        assertThatThrownBy(() -> Pagination.parse(page, size))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }
}
