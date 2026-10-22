// Steps
// Describe a product as returned by the API.
// Build the response from a Product entity.
package com.orderflow.product.dto;

import com.orderflow.product.Product;
import java.math.BigDecimal;

// Similar to a Pydantic response_model: the API shape is kept separate from the database entity.
public record ProductResponse(Long id, String name, BigDecimal price) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice());
    }
}
