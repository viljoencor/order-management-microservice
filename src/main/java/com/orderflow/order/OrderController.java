// Steps
// Validate the incoming order request.
// Call the order service.
// Return 201 with a Location header, 200 for reads.
package com.orderflow.order;

import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Similar to another APIRouter in FastAPI. It only translates HTTP to service calls.
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@ApiResponse(responseCode = "401", description = "Missing or invalid credentials")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create an order",
            description = "Prices and totals are calculated by the server from the current product prices.")
    @ApiResponse(responseCode = "201", description = "Order created")
    @ApiResponse(responseCode = "400", description = "Invalid request, or the same product appears twice")
    @ApiResponse(responseCode = "404", description = "One or more products do not exist (see missingProductIds)")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = OrderResponse.from(orderService.create(request));
        // The service method is @Transactional, so the order has been committed by the time it returns.
        log.info("Order created order_id={} items={}", created.id(), created.items().size());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order with its items")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public OrderResponse getById(@PathVariable Long id) {
        return OrderResponse.from(orderService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List all orders")
    public List<OrderResponse> list() {
        return orderService.list().stream().map(OrderResponse::from).toList();
    }
}
