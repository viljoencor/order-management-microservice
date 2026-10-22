// Steps
// Provide save, find-by-id and find-all for products.
// List products in ascending id order.
package com.orderflow.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// Similar to a repository class around a SQLAlchemy session, but Spring Data
// generates the implementation at startup from this interface, so there is no body to write.
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByIdAsc();
}
