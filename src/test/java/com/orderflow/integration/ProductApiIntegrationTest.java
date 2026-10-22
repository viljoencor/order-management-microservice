// Steps
// Exercise the product endpoints through MockMvc with the real context and H2.
// Check each invalid input gets 400 and creates no row.
// Check missing credentials get 401.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Similar to FastAPI's TestClient against a real app and database, with test credentials
// passed in as properties instead of dependency overrides.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
class ProductApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    // Each test starts from an empty table so tests do not depend on run order.
    @BeforeEach
    void clearProducts() {
        productRepository.deleteAll();
    }

    private ResultActions postProduct(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/products")
                .with(httpBasic("test-user", "test-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions getAuthenticated(String path) throws Exception {
        return mockMvc.perform(get(path).with(httpBasic("test-user", "test-password")));
    }

    @Test
    void validProductIsCreatedWithLocationHeader() throws Exception {
        postProduct("{\"name\":\"Keyboard\",\"price\":450.00}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/products/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(450.00));

        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void nameIsTrimmedAndClientSuppliedIdIsIgnored() throws Exception {
        postProduct("{\"id\":999,\"name\":\"  Mouse  \",\"price\":25.5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mouse"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(999)))
                .andExpect(jsonPath("$.price").value(25.50));

        assertThat(productRepository.existsById(999L)).isFalse();
    }

    @Test
    void createdProductCanBeRetrievedFromLocationHeader() throws Exception {
        String location = postProduct("{\"name\":\"Keyboard\",\"price\":450.00}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(httpBasic("test-user", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(450.00));
    }

    @Test
    void listReturnsProductsInIdOrder() throws Exception {
        productRepository.save(new Product("First", new BigDecimal("1.00")));
        productRepository.save(new Product("Second", new BigDecimal("2.00")));

        getAuthenticated("/api/v1/products")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[1].name").value("Second"));
    }

    @Test
    void listIsEmptyArrayWhenNoProductsExist() throws Exception {
        getAuthenticated("/api/v1/products")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        getAuthenticated("/api/v1/products/12345")
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"\",\"price\":10.00}",
            "{\"name\":\"   \",\"price\":10.00}",
            "{\"name\":\"Keyboard\",\"price\":0}",
            "{\"name\":\"Keyboard\",\"price\":-5.00}",
            "{\"name\":\"Keyboard\",\"price\":10.999}",
            "{\"price\":10.00}",
            "{\"name\":\"Keyboard\"}",
            "{\"name\":null,\"price\":null}",
            "{}"
    })
    void invalidRequestReturns400AndCreatesNothing(String json) throws Exception {
        postProduct(json).andExpect(status().isBadRequest());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    void tooLongNameReturns400() throws Exception {
        postProduct("{\"name\":\"" + "a".repeat(121) + "\",\"price\":10.00}")
                .andExpect(status().isBadRequest());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/products/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":10.00}"))
                .andExpect(status().isUnauthorized());

        assertThat(productRepository.count()).isZero();
    }
}
