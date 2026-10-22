// Steps
// Test create, including name trimming.
// Test get by id and the 404 for a missing product.
// Test list, including the empty case.
package com.orderflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.product.dto.CreateProductRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Similar to a pytest test that replaces the database layer with unittest.mock.
// No Spring context is started, so these run quickly.
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void createSavesProductWithGivenPrice() {
        ProductService service = new ProductService(productRepository);
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product created = service.create(new CreateProductRequest("Keyboard", new BigDecimal("450.50")));

        assertThat(created.getName()).isEqualTo("Keyboard");
        assertThat(created.getPrice()).isEqualByComparingTo("450.50");
    }

    @Test
    void createTrimsSurroundingWhitespaceFromName() {
        ProductService service = new ProductService(productRepository);
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new CreateProductRequest("  Keyboard  ", new BigDecimal("10.00")));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Keyboard");
    }

    @Test
    void getByIdReturnsExistingProduct() {
        ProductService service = new ProductService(productRepository);
        Product product = new Product("Mouse", new BigDecimal("25.00"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThat(service.getById(1L)).isSameAs(product);
    }

    @Test
    void getByIdThrowsNotFoundForMissingProduct() {
        ProductService service = new ProductService(productRepository);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listReturnsProductsFromRepository() {
        ProductService service = new ProductService(productRepository);
        List<Product> products = List.of(
                new Product("Mouse", new BigDecimal("25.00")),
                new Product("Keyboard", new BigDecimal("450.00")));
        when(productRepository.findAllByOrderByIdAsc()).thenReturn(products);

        assertThat(service.list()).containsExactlyElementsOf(products);
    }

    @Test
    void listReturnsEmptyListWhenNoProductsExist() {
        ProductService service = new ProductService(productRepository);
        when(productRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
    }
}
