// Steps
// Check validation, malformed JSON, 404 and business errors return the ProblemDetail contract.
// Check error responses hide internals and that failed orders save nothing.
// Check authentication failures still return 401 and valid credentials still work.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

// Similar to FastAPI tests that assert on the shape of error JSON, not only the status code.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The H2 database is shared with the other integration tests, so clean up before and after.
    @BeforeEach
    @AfterEach
    void clearData() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    private ResultActions postProduct(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/products")
                .with(httpBasic("test-user", "test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
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

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void blankProductNameReturnsStructuredValidationError() throws Exception {
        postProduct("{\"name\":\"  \",\"price\":10.00}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/products"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
    }

    @Test
    void invalidPriceReturnsFieldErrorForPrice() throws Exception {
        postProduct("{\"name\":\"Keyboard\",\"price\":10.999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
    }

    @Test
    void zeroPriceReturnsFieldErrorForPrice() throws Exception {
        postProduct("{\"name\":\"Keyboard\",\"price\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be greater than 0"));
    }

    @Test
    void missingProductFieldsAreListedInAlphabeticalOrder() throws Exception {
        postProduct("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("price"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{not valid json",
            "{\"name\":\"Keyboard\",\"price\":\"abc\"}"
    })
    void malformedProductJsonReturnsGenericError(String json) throws Exception {
        postProduct(json)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Malformed or invalid JSON request"))
                .andExpect(jsonPath("$.instance").value("/api/v1/products"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"items\":[{\"productId\":1,\"quantity\":1.5}]}",
            "{\"items\":[{\"productId\":1,\"quantity\":\"two\"}]}",
            "{\"items\":\"not a list\"}"
    })
    void malformedOrderJsonReturnsGenericErrorWithoutInternalDetails(String json) throws Exception {
        String body = postOrder(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed or invalid JSON request"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception", "jackson", "java.lang", "com.orderflow");
        assertThat(count("orders")).isZero();
    }

    @Test
    void emptyOrderItemsReturnsFieldError() throws Exception {
        postOrder("{\"items\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/orders"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
    }

    @Test
    void invalidQuantityAndMissingProductIdReturnIndexedFieldErrors() throws Exception {
        postOrder("{\"items\":[{\"productId\":1,\"quantity\":0},{\"quantity\":2}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].quantity"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("items[1].productId"));
    }

    @Test
    void duplicateProductIdsReturnClearMessageAndSaveNothing() throws Exception {
        Product keyboard = productRepository.save(new Product("Keyboard", new BigDecimal("450.00")));

        postOrder("{\"items\":[{\"productId\":" + keyboard.getId() + ",\"quantity\":1},"
                + "{\"productId\":" + keyboard.getId() + ",\"quantity\":2}]}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail", containsString("Duplicate product IDs are not allowed")));

        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    @Test
    void unknownProductReturnsStructured404() throws Exception {
        getAuthenticated("/api/v1/products/12345")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/products/12345"));
    }

    @Test
    void unknownOrderReturnsStructured404() throws Exception {
        getAuthenticated("/api/v1/orders/12345")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Order not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/orders/12345"));
    }

    @Test
    void missingProductReferencesReturn404WithMissingProductIdsAndSaveNothing() throws Exception {
        Product keyboard = productRepository.save(new Product("Keyboard", new BigDecimal("450.00")));

        postOrder("{\"items\":[{\"productId\":" + keyboard.getId() + ",\"quantity\":1},"
                + "{\"productId\":900020,\"quantity\":1},{\"productId\":900010,\"quantity\":1}]}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("One or more products were not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/orders"))
                .andExpect(jsonPath("$.missingProductIds", contains(900010, 900020)));

        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    @Test
    void requestWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithIncorrectCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/products").with(httpBasic("test-user", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders").with(httpBasic("someone-else", "test-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithValidCredentialsStillWorks() throws Exception {
        getAuthenticated("/api/v1/products").andExpect(status().isOk());
        getAuthenticated("/api/v1/orders").andExpect(status().isOk());
    }
}
