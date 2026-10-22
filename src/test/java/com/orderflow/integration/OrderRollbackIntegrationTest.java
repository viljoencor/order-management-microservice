// Steps
// Make the database reject an order item after the order row was inserted.
// Check the transaction rolls back and leaves no order or item rows.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import com.orderflow.order.OrderService;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderItemRequest;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

// Unlike the missing-product test, which fails before anything is written, this lets the order row
// be inserted and then has the database reject the item row (foreign key to a product that does not
// exist). Only a rollback can leave the tables empty afterwards.
// The mocked repository gives this class its own Spring context, so it also gets its own H2 database.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password",
        "spring.datasource.url=jdbc:h2:mem:rollbacktest;DB_CLOSE_DELAY=-1"
})
class OrderRollbackIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProductRepository productRepository;

    @Test
    void failureAfterOrderRowIsWrittenRollsBackTheWholeOrder() {
        Product ghost = new Product("Not in the database", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(ghost, "id", 424242L);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(ghost));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(424242L, 1)));

        assertThatThrownBy(() -> orderService.create(request)).isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from order_items", Integer.class)).isZero();
    }
}
