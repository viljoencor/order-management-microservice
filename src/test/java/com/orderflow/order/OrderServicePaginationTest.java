// Steps
// Check order paging fetches the page of ids first, then only those orders, keeping the total.
// Check an empty page of ids does not trigger the second query.
package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class OrderServicePaginationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    private final Pageable pageable = PageRequest.of(1, 2, Sort.by("id").ascending());

    @Test
    void listPagesIdsFirstThenLoadsOnlyThoseOrders() {
        OrderService service = new OrderService(orderRepository, productRepository);
        Order third = new Order(List.of(), new BigDecimal("3.00"));
        Order fourth = new Order(List.of(), new BigDecimal("4.00"));
        when(orderRepository.findIds(pageable)).thenReturn(new PageImpl<>(List.of(3L, 4L), pageable, 5));
        when(orderRepository.findAllByIdInOrderByIdAsc(List.of(3L, 4L))).thenReturn(List.of(third, fourth));

        Page<Order> result = service.list(pageable);

        assertThat(result.getContent()).containsExactly(third, fourth);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);
    }

    @Test
    void outOfRangePageIsEmptyAndSkipsTheSecondQuery() {
        OrderService service = new OrderService(orderRepository, productRepository);
        when(orderRepository.findIds(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 3));

        Page<Order> result = service.list(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(3);
        verify(orderRepository, never()).findAllByIdInOrderByIdAsc(any());
    }
}
