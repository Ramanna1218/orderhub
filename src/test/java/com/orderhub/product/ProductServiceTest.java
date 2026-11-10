package com.orderhub.product;

import com.orderhub.common.exception.ConflictException;
import com.orderhub.common.exception.DuplicateResourceException;
import com.orderhub.common.exception.ResourceNotFoundException;
import com.orderhub.order.OrderItemRepository;
import com.orderhub.product.ProductDtos.ProductRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;
    @Mock
    private OrderItemRepository orderItems;

    @InjectMocks
    private ProductService productService;

    private static ProductRequest request(String sku) {
        return new ProductRequest(sku, "Widget", "A widget", new BigDecimal("9.99"), 3);
    }

    @Test
    void create_trimsAndSaves() {
        when(products.existsBySkuIgnoreCase("W-1")).thenReturn(false);
        when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = productService.create(request("  W-1  "));

        assertThat(response.sku()).isEqualTo("W-1");
        assertThat(response.stockQuantity()).isEqualTo(3);
    }

    @Test
    void create_rejectsDuplicateSku() {
        when(products.existsBySkuIgnoreCase("W-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request("W-1")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(products, never()).save(any());
    }

    @Test
    void update_unknownProduct_throwsNotFound() {
        when(products.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(42L, request("W-1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_productOnAnOrder_isRejected() {
        when(products.findById(7L)).thenReturn(Optional.of(new Product("W-1", "Widget", null, BigDecimal.ONE, 1)));
        when(orderItems.existsByProductId(7L)).thenReturn(true);

        assertThatThrownBy(() -> productService.delete(7L)).isInstanceOf(ConflictException.class);
        verify(products, never()).delete(any());
    }
}
