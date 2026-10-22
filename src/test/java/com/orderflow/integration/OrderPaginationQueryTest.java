// Steps
// Count the SQL statements and loaded entities while one page of orders is listed.
// Check only the requested orders are loaded, and the statement count does not grow with the page size.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.order.Order;
import com.orderflow.order.OrderItem;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderService;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// Checks the database does the paging, not Java. Hibernate statistics are only switched on here,
// which needs a separate Spring context, so this class also has its own in-memory H2 database name.
// Roughly like asserting on SQLAlchemy's echoed SQL or an event listener that counts queries.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password",
        "spring.datasource.url=jdbc:h2:mem:paginationquery;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class OrderPaginationQueryTest {

    private static final int ORDERS = 8;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void seedOrders() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        Product keyboard = productRepository.save(new Product("Keyboard", new BigDecimal("450.00")));
        Product mouse = productRepository.save(new Product("Mouse", new BigDecimal("19.99")));
        for (int i = 1; i <= ORDERS; i++) {
            List<OrderItem> items = List.of(
                    new OrderItem(keyboard, i, keyboard.getPrice()),
                    new OrderItem(mouse, 1, mouse.getPrice()));
            orderRepository.save(new Order(items, keyboard.getPrice().multiply(BigDecimal.valueOf(i)).add(mouse.getPrice())));
        }
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void onlyTheRequestedOrdersAreLoadedFromTheDatabase() {
        statistics.clear();

        Page<Order> page = orderService.list(PageRequest.of(0, 3, Sort.by("id").ascending()));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(ORDERS);
        assertThat(page.getContent()).allSatisfy(order -> assertThat(order.getItems()).hasSize(2));
        // Loading all eight orders and slicing in Java would show 8 here.
        assertThat(statistics.getEntityStatistics(Order.class.getName()).getLoadCount()).isEqualTo(3);
        assertThat(statistics.getEntityStatistics(OrderItem.class.getName()).getLoadCount()).isEqualTo(6);
    }

    @Test
    void statementCountStaysConstantAndDoesNotGrowWithPageSize() {
        statistics.clear();
        orderService.list(PageRequest.of(0, 2, Sort.by("id").ascending()));
        long smallPage = statistics.getPrepareStatementCount();

        statistics.clear();
        orderService.list(PageRequest.of(0, 6, Sort.by("id").ascending()));
        long largePage = statistics.getPrepareStatementCount();

        // Page of ids, total count, then the orders with items and products: no query per order.
        assertThat(smallPage).isEqualTo(3);
        assertThat(largePage).isEqualTo(3);
    }
}
