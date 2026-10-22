// Steps
// Accept name and price for a new product.
// Reject blank names and invalid prices before the service runs.
package com.orderflow.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Similar to a Pydantic request model. The difference: Pydantic validates while parsing,
// whereas here Jackson parses first and Spring runs these rules when the controller uses @Valid.
// There is no id field on purpose, so a client-supplied id is simply ignored.
public record CreateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price) {
}
