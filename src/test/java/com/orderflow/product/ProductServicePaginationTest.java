// Steps
// Check the product service hands the Pageable to the repository and returns its page.
// Check an empty page is returned as is.
package com.orderflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProductServicePaginationTest {

    @Mock
    private ProductRepository productRepository;

    private final Pageable pageable = PageRequest.of(1, 2, Sort.by("id").ascending());

    @Test
    void listReturnsTheRepositoryPage() {
        ProductService service = new ProductService(productRepository);
        Page<Product> page = new PageImpl<>(List.of(
                new Product("Third", new BigDecimal("3.00")),
                new Product("Fourth", new BigDecimal("4.00"))), pageable, 5);
        when(productRepository.findAll(pageable)).thenReturn(page);

        Page<Product> result = service.list(pageable);

        assertThat(result.getContent()).extracting(Product::getName).containsExactly("Third", "Fourth");
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void listReturnsEmptyPageWhenPageIsOutOfRange() {
        ProductService service = new ProductService(productRepository);
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 3));

        Page<Product> result = service.list(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(3);
    }
}
