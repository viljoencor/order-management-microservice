// Steps
// Save and find orders.
// Load items and their products in the same query to avoid N+1 selects.
// List orders in ascending id order.
package com.orderflow.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Similar to SQLAlchemy's joinedload()/selectinload(): the entity graph says which relations
// to fetch up front. Without it, each order would trigger extra queries for its items.
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findById(Long id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findAllByOrderByIdAsc();

    // Step 1 of paging orders: only ids, so the database can apply LIMIT/OFFSET safely.
    // Joining the items collection here would make Hibernate page in memory instead.
    @Query(value = "select o.id from Order o", countQuery = "select count(o) from Order o")
    Page<Long> findIds(Pageable pageable);

    // Step 2: load just those orders with their items and products in one query.
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findAllByIdInOrderByIdAsc(Collection<Long> ids);
}
