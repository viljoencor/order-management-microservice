// Steps
// Reject duplicate product ids and load the referenced products.
// Reject the whole order if any product is missing, listing every missing id.
// Copy current prices into the items and calculate the total.
// Save the order and its items in one transaction.
package com.orderflow.order;

import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderItemRequest;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    // Largest value that fits DECIMAL(19,2).
    private static final BigDecimal MAX_TOTAL = new BigDecimal("99999999999999999.99");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    // Similar to "with session.begin():" in SQLAlchemy: everything in this method commits together,
    // and a RuntimeException rolls it all back. All checks run before the single save.
    @Transactional
    public Order create(CreateOrderRequest request) {
        List<OrderItemRequest> requested = request.items();
        rejectDuplicateProducts(requested);

        List<Long> productIds = requested.stream().map(OrderItemRequest::productId).toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        rejectMissingProducts(productIds, products);

        List<OrderItem> items = requested.stream()
                .map(item -> {
                    Product product = products.get(item.productId());
                    return new OrderItem(product, item.quantity(), product.getPrice());
                })
                .toList();

        BigDecimal total = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total is too large");
        }

        return orderRepository.save(new Order(items, total));
    }

    // Similar to a read-only session: the database may skip dirty checking and flushing.
    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Transactional(readOnly = true)
    public List<Order> list() {
        return orderRepository.findAllByOrderByIdAsc();
    }

    // Repeated ids are rejected rather than guessing whether the client wanted separate lines.
    private void rejectDuplicateProducts(List<OrderItemRequest> requested) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest item : requested) {
            if (!seen.add(item.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate product IDs are not allowed in one order (product " + item.productId() + ")");
            }
        }
    }

    private void rejectMissingProducts(List<Long> productIds, Map<Long, Product> found) {
        List<Long> missing = productIds.stream().filter(id -> !found.containsKey(id)).sorted().toList();
        if (!missing.isEmpty()) {
            ResponseStatusException error = new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "One or more products were not found");
            // Becomes an extra field in the ProblemDetail response body.
            error.getBody().setProperty("missingProductIds", missing);
            throw error;
        }
    }
}
