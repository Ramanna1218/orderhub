package com.orderhub.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            int stockQuantity,
            Instant createdAt,
            Instant updatedAt) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                    p.getPrice(), p.getStockQuantity(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
