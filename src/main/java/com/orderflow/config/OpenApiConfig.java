// Steps
// Describe the API with a title, version and description.
// Add an HTTP Basic security scheme so Swagger UI shows an Authorize button.
package com.orderflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Similar to the title/version/description arguments of FastAPI(...). The difference is that
// FastAPI builds the spec from route signatures and Pydantic models, while Springdoc reads
// the Spring controllers and DTO records; only the metadata and auth scheme are written by hand.
// The documentation URLs stay behind HTTP Basic: SecurityConfig has no exception for them.
@Configuration
public class OpenApiConfig {

    static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI orderflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow API")
                        .version("1.0.0")
                        .description("A small Spring Boot service for managing products and orders."))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
