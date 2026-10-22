// Steps
// Check the OpenAPI document and Swagger UI require HTTP Basic credentials.
// Check the document describes all six endpoints and the basicAuth scheme.
// Check Swagger UI's page, config and static assets load once authenticated.
package com.orderflow.integration;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Similar to checking FastAPI's /openapi.json and /docs, except these URLs are behind authentication here.
@SpringBootTest(properties = {
        "orderflow.auth.user=test-user",
        "orderflow.auth.password=test-password"
})
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions getAuthenticated(String path) throws Exception {
        return mockMvc.perform(get(path).with(httpBasic("test-user", "test-password")));
    }

    @Test
    void documentationRequiresCredentials() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isUnauthorized());
    }

    @Test
    void documentationRejectsWrongCredentials() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("test-user", "wrong")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html").with(httpBasic("test-user", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentHasMetadataAndDescribesAllSixEndpoints() throws Exception {
        getAuthenticated("/v3/api-docs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("OrderFlow API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths['/api/v1/products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}'].get").exists());
    }

    @Test
    void openApiDocumentDescribesStatusCodesAndRequestAndResponseSchemas() throws Exception {
        getAuthenticated("/v3/api-docs")
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses", hasKey("401")))
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].get.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.components.schemas", hasKey("CreateOrderRequest")))
                .andExpect(jsonPath("$.components.schemas", hasKey("OrderResponse")))
                .andExpect(jsonPath("$.components.schemas", hasKey("CreateProductRequest")))
                .andExpect(jsonPath("$.components.schemas", hasKey("ProductResponse")));
    }

    @Test
    void openApiDocumentDeclaresHttpBasicAuthentication() throws Exception {
        getAuthenticated("/v3/api-docs")
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.security[0].basicAuth").exists());
    }

    @Test
    void swaggerUiPageConfigAndAssetsLoadWithCredentials() throws Exception {
        getAuthenticated("/swagger-ui/index.html").andExpect(status().isOk());
        getAuthenticated("/v3/api-docs/swagger-config").andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
        getAuthenticated("/swagger-ui/swagger-ui-bundle.js").andExpect(status().isOk());
        getAuthenticated("/swagger-ui/swagger-ui.css").andExpect(status().isOk());
    }
}
