// Steps
// Describe an order as returned by the API.
// Build it from an Order entity without exposing the entity.
package com.orderflow.order.dto;

import com.orderflow.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Similar to a Pydantic response_model that nests the item models.
public record OrderResponse(Long id, List<OrderItemResponse> items, BigDecimal totalPrice, Instant createdAt) {

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream().map(OrderItemResponse::from).toList();
        return new OrderResponse(order.getId(), items, order.getTotalPrice(), order.getCreatedAt());
    }
}
