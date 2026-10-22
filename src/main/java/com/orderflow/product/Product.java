// Steps
// Map the products table with a generated id, name and DECIMAL(19,2) price.
// Expose only getters; products are not updated in this phase.
package com.orderflow.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

// Similar to a SQLAlchemy model class. Unlike SQLAlchemy, JPA needs a no-argument
// constructor so Hibernate can create instances when it loads rows.
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    // BigDecimal avoids the rounding errors of double/float, which matters for money.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    protected Product() {
    }

    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
