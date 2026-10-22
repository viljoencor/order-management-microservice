// Steps
// Check every response carries an X-Request-ID, generated or preserved when it is safe.
// Check unsafe request IDs are replaced and status codes are unchanged.
// Check the completion log line, the order-created log, and that credentials are never logged.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.order.OrderRepository;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Similar to pytest's caplog fixture: OutputCaptureExtension captures what the app wrote to the console.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIntegrationTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    // The H2 database is shared with the other integration tests, so clean up before and after.
    @BeforeEach
    @AfterEach
    void clearData() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void missingRequestIdIsReplacedWithGeneratedUuid() throws Exception {
        String id = mockMvc.perform(get("/api/v1/products").with(httpBasic("test-user", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-ID");

        assertThat(id).matches(UUID_PATTERN);
    }

    @Test
    void validSuppliedRequestIdIsPreserved() throws Exception {
        String id = mockMvc.perform(get("/api/v1/products")
                        .header("X-Request-ID", "client-id_42.A")
                        .with(httpBasic("test-user", "test-password")))
                .andReturn().getResponse().getHeader("X-Request-ID");

        assertThat(id).isEqualTo("client-id_42.A");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "has spaces",
            "semi;colon",
            "inject%0Aline",
            "<script>",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    void unsafeOrOversizedRequestIdIsReplaced(String unsafe) throws Exception {
        String id = mockMvc.perform(get("/api/v1/products")
                        .header("X-Request-ID", unsafe)
                        .with(httpBasic("test-user", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-ID");

        assertThat(id).isNotEqualTo(unsafe).matches(UUID_PATTERN);
    }

    @Test
    void unauthenticatedRequestStillGetsRequestIdAndKeeps401() throws Exception {
        String id = mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader("X-Request-ID");

        assertThat(id).matches(UUID_PATTERN);
    }

    @Test
    void errorStatusCodesAreUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/orders/12345").with(httpBasic("test-user", "test-password")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/products")
                        .with(httpBasic("test-user", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completionLineHasRequestIdMethodPathStatusAndDurationWithoutQueryString(CapturedOutput output)
            throws Exception {
        mockMvc.perform(get("/api/v1/products")
                .queryParam("secret", "do-not-log-me")
                .header("X-Request-ID", "log-test-1")
                .with(httpBasic("test-user", "test-password")));

        assertThat(output.getAll())
                .containsPattern("request_id=log-test-1 method=GET path=/api/v1/products status=200 duration_ms=\\d+")
                .doesNotContain("do-not-log-me");
    }

    @Test
    void rejectedRequestIsLoggedWithItsStatus(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/products").header("X-Request-ID", "log-test-401"));

        assertThat(output.getAll())
                .containsPattern("request_id=log-test-401 method=GET path=/api/v1/products status=401");
    }

    @Test
    void credentialsAndBodiesAreNeverLogged(CapturedOutput output) throws Exception {
        String encoded = Base64.getEncoder().encodeToString("test-user:test-password".getBytes());

        mockMvc.perform(post("/api/v1/products")
                .with(httpBasic("test-user", "test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"UniqueBodyMarker\",\"price\":10.00}"));

        assertThat(output.getAll())
                .doesNotContain("test-password")
                .doesNotContain(encoded)
                .doesNotContain("Authorization")
                .doesNotContain("UniqueBodyMarker");
    }

    @Test
    void successfulOrderIsLoggedOnceWithOrderIdAndItemCount(CapturedOutput output) throws Exception {
        Product keyboard = productRepository.save(new Product("Keyboard", new BigDecimal("450.00")));

        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("test-user", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + keyboard.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated());

        assertThat(output.getAll()).containsPattern("Order created order_id=\\d+ items=1");
        assertThat(output.getAll().split("Order created order_id=", -1)).hasSize(2);
    }

    @Test
    void rejectedOrderIsNotLoggedAsCreated(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("test-user", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":900001,\"quantity\":1}]}"))
                .andExpect(status().isNotFound());

        assertThat(output.getAll()).doesNotContain("Order created");
    }
}
