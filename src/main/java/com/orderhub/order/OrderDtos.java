package com.orderhub.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(1000) Integer quantity) {
    }

    public record CreateOrderRequest(
            @NotNull Long customerId,
            @NotEmpty @Size(max = 50) List<@Valid @NotNull OrderItemRequest> items) {
    }

    public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
    }

    public record OrderItemResponse(
            Long productId,
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getProduct().getId(), item.getProduct().getSku(),
                    item.getProduct().getName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
        }
    }

    public record OrderResponse(
            Long id,
            Long customerId,
            String customerName,
            OrderStatus status,
            BigDecimal totalAmount,
            List<OrderItemResponse> items,
            Instant createdAt,
            Instant updatedAt) {

        public static OrderResponse from(CustomerOrder order) {
            return new OrderResponse(
                    order.getId(),
                    order.getCustomer().getId(),
                    order.getCustomer().getFullName(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getItems().stream().map(OrderItemResponse::from).toList(),
                    order.getCreatedAt(),
                    order.getUpdatedAt());
        }
    }
}
