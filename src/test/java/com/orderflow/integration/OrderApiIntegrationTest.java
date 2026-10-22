// Steps
// Exercise the order endpoints through MockMvc with the real context and H2.
// Check prices are snapshotted, totals are exact and failed orders leave no rows.
// Check invalid input gets 400 and missing credentials get 401.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.order.OrderRepository;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Similar to FastAPI's TestClient against a real app and database.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The H2 database is shared with the other integration tests, so clean up both before and after.
    // Orders go first because order_items reference products.
    @BeforeEach
    @AfterEach
    void clearData() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    private Product saveProduct(String name, String price) {
        return productRepository.save(new Product(name, new BigDecimal(price)));
    }

    private ResultActions postOrder(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .with(httpBasic("test-user", "test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions getAuthenticated(String path) throws Exception {
        return mockMvc.perform(get(path).with(httpBasic("test-user", "test-password")));
    }

    private String order(Object... idsAndQuantities) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < idsAndQuantities.length; i += 2) {
            if (i > 0) {
                items.append(",");
            }
            items.append("{\"productId\":").append(idsAndQuantities[i])
                    .append(",\"quantity\":").append(idsAndQuantities[i + 1]).append("}");
        }
        return "{\"items\":[" + items + "]}";
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void validOrderIsCreatedWithLocationHeaderAndCalculatedTotals() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");
        Product mouse = saveProduct("Mouse", "19.99");

        postOrder(order(keyboard.getId(), 2, mouse.getId(), 3))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/orders/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productName").value("Keyboard"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(450.00))
                .andExpect(jsonPath("$.items[0].lineTotal").value(900.00))
                .andExpect(jsonPath("$.items[1].lineTotal").value(59.97))
                .andExpect(jsonPath("$.totalPrice").value(959.97));
    }

    @Test
    void createdOrderCanBeRetrievedAndItsRowsArePersisted() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");
        Product mouse = saveProduct("Mouse", "19.99");

        String location = postOrder(order(keyboard.getId(), 2, mouse.getId(), 1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(httpBasic("test-user", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId").value(keyboard.getId()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[1].productName").value("Mouse"))
                .andExpect(jsonPath("$.totalPrice").value(919.99));

        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("order_items")).isEqualTo(2);
    }

    @Test
    void totalKeepsExactDecimalPrecision() throws Exception {
        Product tenCents = saveProduct("Sticker", "0.10");

        // 0.10 x 3 is 0.30; with double arithmetic this style of sum drifts.
        postOrder(order(tenCents.getId(), 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(0.30));

        assertThat(jdbcTemplate.queryForObject("select total_price from orders", BigDecimal.class))
                .isEqualByComparingTo("0.30");
    }

    @Test
    void laterProductPriceChangesDoNotRewriteOrderHistory() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");
        String location = postOrder(order(keyboard.getId(), 1))
                .andReturn().getResponse().getHeader("Location");

        jdbcTemplate.update("update products set price = 999.00 where id = ?", keyboard.getId());

        mockMvc.perform(get(location).with(httpBasic("test-user", "test-password")))
                .andExpect(jsonPath("$.items[0].unitPrice").value(450.00))
                .andExpect(jsonPath("$.totalPrice").value(450.00));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        getAuthenticated("/api/v1/orders/12345").andExpect(status().isNotFound());
    }

    @Test
    void missingProductsReturn404ListingEveryMissingIdAndSaveNothing() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");

        postOrder(order(keyboard.getId(), 1, 900002, 1, 900001, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.missingProductIds", contains(900001, 900002)));

        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    @Test
    void duplicateProductIdsReturn400AndSaveNothing() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");

        postOrder(order(keyboard.getId(), 1, keyboard.getId(), 2)).andExpect(status().isBadRequest());

        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"items\":[]}",
            "{\"items\":null}",
            "{}",
            "{\"items\":[null]}",
            "{\"items\":[{\"productId\":1,\"quantity\":0}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":-2}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":1001}]}",
            "{\"items\":[{\"productId\":1}]}",
            "{\"items\":[{\"quantity\":1}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":1.5}]}"
    })
    void invalidRequestReturns400AndSavesNothing(String json) throws Exception {
        saveProduct("Keyboard", "450.00");

        postOrder(json).andExpect(status().isBadRequest());

        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    @Test
    void orderTotalTooLargeForTheDatabaseReturns400AndSavesNothing() throws Exception {
        Product expensive = saveProduct("Gold bar", "99999999999999999.99");

        postOrder(order(expensive.getId(), 2)).andExpect(status().isBadRequest());

        assertThat(count("orders")).isZero();
    }

    @Test
    void clientSuppliedPricesAndTotalsAreIgnored() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");

        postOrder("{\"totalPrice\":1.00,\"items\":[{\"productId\":" + keyboard.getId()
                + ",\"quantity\":1,\"unitPrice\":1.00}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(450.00))
                .andExpect(jsonPath("$.totalPrice").value(450.00));
    }

    @Test
    void listReturnsPersistedOrdersInIdOrder() throws Exception {
        Product keyboard = saveProduct("Keyboard", "450.00");
        postOrder(order(keyboard.getId(), 1)).andExpect(status().isCreated());
        postOrder(order(keyboard.getId(), 3)).andExpect(status().isCreated());

        getAuthenticated("/api/v1/orders")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].totalPrice").value(450.00))
                .andExpect(jsonPath("$[1].totalPrice").value(1350.00))
                .andExpect(jsonPath("$[1].items[0].productName").value("Keyboard"));
    }

    @Test
    void listIsEmptyArrayWhenNoOrdersExist() throws Exception {
        getAuthenticated("/api/v1/orders")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order(1, 1)))
                .andExpect(status().isUnauthorized());
    }
}
