// Steps
// Validate the incoming product request.
// Call the product service.
// Return 201 with a Location header, 200 for reads.
package com.orderflow.product;

import com.orderflow.common.Pagination;
import com.orderflow.product.dto.CreateProductRequest;
import com.orderflow.product.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Similar to an APIRouter in FastAPI. The controller handles HTTP, not business logic.
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
@ApiResponse(responseCode = "401", description = "Missing or invalid credentials")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // @Valid is what triggers the request rules; in FastAPI this happens implicitly.
    // A failure becomes a 400 ProblemDetail response through GlobalExceptionHandler.
    @PostMapping
    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "400", description = "Invalid name or price")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = ProductResponse.from(productService.create(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List products",
            description = "Returns all products. Send both page (from 0) and size (1 to 100) to get one page, "
                    + "sorted by id, with X-Total-Count, X-Total-Pages, X-Page and X-Page-Size response headers.")
    @ApiResponse(responseCode = "400", description = "Only one of page and size, or an invalid value")
    public ResponseEntity<List<ProductResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Optional<Pageable> pageable = Pagination.parse(page, size);
        if (pageable.isEmpty()) {
            return ResponseEntity.ok(productService.list().stream().map(ProductResponse::from).toList());
        }
        Page<Product> result = productService.list(pageable.get());
        return ResponseEntity.ok()
                .headers(Pagination.headers(result))
                .body(result.getContent().stream().map(ProductResponse::from).toList());
    }
}
