// Steps
// Make a test-only controller throw an unexpected exception.
// Check the client gets a plain 500 without exception details, and the request is logged once.
// Check normal endpoints keep working in the same application.
package com.orderflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// MockMvc does not run the servlet container's error handling, so this starts a real server on a
// random port to see what a client would actually receive. Roughly like running uvicorn in a
// pytest fixture and calling it with httpx.
// It has its own H2 database name because a different web environment means a separate Spring context.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "orderflow.auth.user=test-user",
                "orderflow.auth.password=test-password",
                "spring.datasource.url=jdbc:h2:mem:unexpectederror;DB_CLOSE_DELAY=-1"
        })
@Import(UnexpectedErrorIntegrationTest.FailingController.class)
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorIntegrationTest {

    private static final String SECRET = "secret-internal-detail";

    // Lives only in the test sources, so production code has no failing endpoint.
    @RestController
    static class FailingController {

        @GetMapping("/test-only/fail")
        String fail() {
            throw new IllegalStateException(SECRET);
        }
    }

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        String credentials = Base64.getEncoder().encodeToString("test-user:test-password".getBytes());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Basic " + credentials)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
        HttpResponse<String> response = get("/test-only/fail");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .doesNotContain(SECRET)
                .doesNotContain("IllegalStateException")
                .doesNotContain("FailingController")
                .doesNotContain("trace")
                .doesNotContain("at com.orderflow");
    }

    @Test
    void unexpectedExceptionIsLoggedOnceByTheRequestFilterWithStatus500(CapturedOutput output) throws Exception {
        get("/test-only/fail");

        String logs = output.getAll();
        assertThat(logs).containsPattern("path=/test-only/fail status=500");
        assertThat(logs.split("path=/test-only/fail status=", -1)).hasSize(2);
        // The cause stays in the server log so the failure remains diagnosable.
        assertThat(logs).contains(SECRET);
    }

    @Test
    void normalEndpointsStillWorkAlongsideTheFailingOne() throws Exception {
        assertThat(get("/api/v1/products").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/orders/12345").statusCode()).isEqualTo(404);
    }
}
