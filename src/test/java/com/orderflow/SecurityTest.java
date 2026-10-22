// Steps - SecurityTest
// Check the application context loads with test credentials.
// Check requests without credentials get 401.
// Check wrong credentials get 401 and correct ones pass the security layer.
// Python: Like a FastAPI TestClient test with the credentials overridden for the test.
package com.orderflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void requestWithoutCredentialsIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").with(httpBasic("test-user", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithCorrectCredentialsPassesSecurity() throws Exception {
        // No controller exists yet, so a correct login ends in 404 rather than 401.
        mockMvc.perform(get("/api/v1/products").with(httpBasic("test-user", "test-password")))
                .andExpect(status().isNotFound());
    }
}
