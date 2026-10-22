// Steps
// Map the orders table with a generated id, total price and UTC creation time.
// Own the order items so they are saved together with the order.
package com.orderflow.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Similar to a SQLAlchemy model with relationship("OrderItem", cascade="all").
// The order is the aggregate root: saving it saves its items too, so there is no item repository.
// The table is called "orders" because ORDER is a reserved SQL word.
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    // Instant is always UTC. Truncated so the value returned on create matches what H2 stores.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // One-directional on purpose: the join column lives on order_items, but items do not point back.
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("id ASC")
    private List<OrderItem> items;

    protected Order() {
    }

    public Order(List<OrderItem> items, BigDecimal totalPrice) {
        this.items = items;
        this.totalPrice = totalPrice;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
