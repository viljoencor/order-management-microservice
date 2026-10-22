// Steps
// Trim the name and save a new product.
// Find a product by id, or fail with 404.
// List all products in id order.
package com.orderflow.product;

import com.orderflow.product.dto.CreateProductRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Similar to a service module in a FastAPI app that holds the business rules.
// Spring creates it once and passes the repository in through the constructor,
// roughly like Depends() but resolved by the framework at startup.
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(CreateProductRequest request) {
        return productRepository.save(new Product(request.name().trim(), request.price()));
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public List<Product> list() {
        return productRepository.findAllByOrderByIdAsc();
    }

    // The sort (by id) is already inside the Pageable, and the database applies the limit and offset.
    public Page<Product> list(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
}
