// Steps
// Accept a list of items for a new order.
// Reject an empty list or empty entries before the service runs.
package com.orderflow.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// Similar to a Pydantic model with a nested list of models. There are no price or total fields,
// so a client cannot send them. Duplicate product ids are a business rule checked in the service.
public record CreateOrderRequest(@NotEmpty List<@NotNull @Valid OrderItemRequest> items) {
}
