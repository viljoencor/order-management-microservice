// Steps - OrderflowApplication
// Start the Spring Boot application.
// Sit at the package root so component scanning finds product, order and config later.
// Python: Roughly the "app = FastAPI()" plus uvicorn entry point, in one class.
package com.orderflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderflowApplication.class, args);
    }
}
