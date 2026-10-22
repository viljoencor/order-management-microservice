// Steps
// Check paginated product and order listings through HTTP: records, order, headers and empty pages.
// Check the unpaginated response is unchanged and invalid parameters get a 400.
// Check order pages carry the right items, prices and totals, and that authentication still applies.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.order.Order;
import com.orderflow.order.OrderItem;
import com.orderflow.order.OrderRepository;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Similar to FastAPI tests that call a list route with ?skip=...&limit=... and check the result.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
class PaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private final List<Product> products = new ArrayList<>();

    // Five products and five orders: order n has two items, quantities n and n + 1.
    // The H2 database is shared with the other integration tests, so clean up before and after.
    @BeforeEach
    void seed() {
        clearData();
        for (int i = 1; i <= 5; i++) {
            products.add(productRepository.save(new Product("P" + i, new BigDecimal(i + ".50"))));
        }
        for (int n = 1; n <= 5; n++) {
            Product first = products.get(n - 1);
            Product second = products.get(n % 5);
            List<OrderItem> items = List.of(
                    new OrderItem(first, n, first.getPrice()),
                    new OrderItem(second, n + 1, second.getPrice()));
            BigDecimal total = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            orderRepository.save(new Order(items, total));
        }
    }

    @AfterEach
    void clearData() {
        products.clear();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    private ResultActions getAuthenticated(String path) throws Exception {
        return mockMvc.perform(get(path).with(httpBasic("test-user", "test-password")));
    }

    @Test
    void firstProductPageReturnsFirstRecordsInIdOrderWithHeaders() throws Exception {
        getAuthenticated("/api/v1/products?page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("P1"))
                .andExpect(jsonPath("$[1].name").value("P2"));
    }

    @Test
    void laterProductPagesContinueWhereThePreviousOneStopped() throws Exception {
        getAuthenticated("/api/v1/products?page=1&size=2")
                .andExpect(header().string("X-Page", "1"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("P3"))
                .andExpect(jsonPath("$[1].name").value("P4"));
        getAuthenticated("/api/v1/products?page=2&size=2")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("P5"));
    }

    @Test
    void outOfRangeProductPageIsEmptyArrayWith200AndStillHasHeaders() throws Exception {
        getAuthenticated("/api/v1/products?page=3&size=2")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sizeOfOneHundredIsAllowedAndReturnsEverything() throws Exception {
        getAuthenticated("/api/v1/products?page=0&size=100")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Size", "100"))
                .andExpect(header().string("X-Total-Pages", "1"))
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void unpaginatedProductListIsTheFullArrayWithoutPaginationHeaders() throws Exception {
        getAuthenticated("/api/v1/products")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("P1"))
                .andExpect(jsonPath("$[4].name").value("P5"));
    }

    @Test
    void firstOrderPageHasCorrectItemsPricesAndTotals() throws Exception {
        // Order 1: 1 x 1.50 (P1) + 2 x 2.50 (P2) = 6.50. Order 2: 2 x 2.50 (P2) + 3 x 3.50 (P3) = 15.50.
        getAuthenticated("/api/v1/orders?page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].items", hasSize(2)))
                .andExpect(jsonPath("$[0].items[0].productName").value("P1"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(1))
                .andExpect(jsonPath("$[0].items[0].unitPrice").value(1.50))
                .andExpect(jsonPath("$[0].items[0].lineTotal").value(1.50))
                .andExpect(jsonPath("$[0].items[1].productName").value("P2"))
                .andExpect(jsonPath("$[0].items[1].lineTotal").value(5.00))
                .andExpect(jsonPath("$[0].totalPrice").value(6.50))
                .andExpect(jsonPath("$[1].items", hasSize(2)))
                .andExpect(jsonPath("$[1].totalPrice").value(15.50));
    }

    @Test
    void orderPagesAreInStableIdOrderAndDoNotOverlap() throws Exception {
        String firstPage = getAuthenticated("/api/v1/orders?page=0&size=2").andReturn().getResponse().getContentAsString();
        String secondPage = getAuthenticated("/api/v1/orders?page=1&size=2").andReturn().getResponse().getContentAsString();
        List<Long> savedIds = orderRepository.findAll().stream().map(Order::getId).sorted().toList();

        getAuthenticated("/api/v1/orders?page=1&size=2")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(savedIds.get(2)))
                .andExpect(jsonPath("$[1].id").value(savedIds.get(3)));
        assertThat(firstPage).contains("\"id\":" + savedIds.get(0)).contains("\"id\":" + savedIds.get(1));
        assertThat(secondPage).doesNotContain("\"id\":" + savedIds.get(0)).doesNotContain("\"id\":" + savedIds.get(1));
        getAuthenticated("/api/v1/orders?page=2&size=2")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(savedIds.get(4)));
    }

    @Test
    void outOfRangeOrderPageIsEmptyArrayWith200() throws Exception {
        getAuthenticated("/api/v1/orders?page=9&size=2")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unpaginatedOrderListIsTheFullArrayWithoutPaginationHeaders() throws Exception {
        getAuthenticated("/api/v1/orders")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].items", hasSize(2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page=0",
            "size=10",
            "page=-1&size=10",
            "page=0&size=0",
            "page=0&size=-3",
            "page=0&size=101",
            "page=abc&size=10",
            "page=0&size=ten",
            "page=2147483647&size=100"
    })
    void invalidPaginationParametersReturn400ForBothListings(String query) throws Exception {
        getAuthenticated("/api/v1/products?" + query).andExpect(status().isBadRequest());
        getAuthenticated("/api/v1/orders?" + query).andExpect(status().isBadRequest());
    }

    @Test
    void missingPageParameterExplainsWhatIsWrong() throws Exception {
        getAuthenticated("/api/v1/products?size=10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Both page and size must be provided"));
    }

    @Test
    void authenticationStillAppliesToPaginatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/products?page=0&size=2")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders?page=0&size=2")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders?page=0&size=2").with(httpBasic("test-user", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
