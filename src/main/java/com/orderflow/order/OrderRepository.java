// Steps
// Save and find orders.
// Load items and their products in the same query to avoid N+1 selects.
// List orders in ascending id order.
package com.orderflow.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// Similar to SQLAlchemy's joinedload()/selectinload(): the entity graph says which relations
// to fetch up front. Without it, each order would trigger extra queries for its items.
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findById(Long id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findAllByOrderByIdAsc();
}
