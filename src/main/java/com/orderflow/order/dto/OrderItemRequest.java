// Steps
// Accept a product id and a quantity.
// Keep quantity positive and below a sensible upper limit.
package com.orderflow.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Similar to Pydantic's conint(gt=0, le=1000), but checked when Spring runs @Valid.
// Integer (not int) so a missing field is null and gets rejected, instead of silently becoming 0.
public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @Positive @Max(1000) Integer quantity) {
}
