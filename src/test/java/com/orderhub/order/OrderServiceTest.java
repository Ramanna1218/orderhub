package com.orderhub.order;

import com.orderhub.common.exception.InsufficientStockException;
import com.orderhub.common.exception.ResourceNotFoundException;
import com.orderhub.customer.Customer;
import com.orderhub.customer.CustomerRepository;
import com.orderhub.order.OrderDtos.CreateOrderRequest;
import com.orderhub.order.OrderDtos.OrderItemRequest;
import com.orderhub.order.OrderDtos.OrderResponse;
import com.orderhub.product.Product;
import com.orderhub.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private CustomerRepository customers;
    @Mock
    private ProductRepository products;

    @InjectMocks
    private OrderService orderService;

    private Customer customer;
    private Product keyboard;
    private Product mouse;

    @BeforeEach
    void setUp() {
        customer = new Customer("Ada", "Lovelace", "ada@example.com", null);
        ReflectionTestUtils.setField(customer, "id", 1L);

        keyboard = new Product("KB-1", "Keyboard", null, new BigDecimal("49.99"), 10);
        ReflectionTestUtils.setField(keyboard, "id", 100L);

        mouse = new Product("MS-1", "Mouse", null, new BigDecimal("19.50"), 5);
        ReflectionTestUtils.setField(mouse, "id", 200L);
    }

    @Test
    void createOrder_decrementsStockAndComputesTotal() {
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.of(keyboard));
        when(products.findByIdForUpdate(200L)).thenReturn(Optional.of(mouse));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.create(new CreateOrderRequest(1L, List.of(
                new OrderItemRequest(100L, 2),
                new OrderItemRequest(200L, 3))));

        assertThat(response.totalAmount()).isEqualByComparingTo("158.48"); // 2*49.99 + 3*19.50
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(2);
        assertThat(keyboard.getStockQuantity()).isEqualTo(8);
        assertThat(mouse.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void createOrder_mergesDuplicateProductLines() {
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.of(keyboard));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.create(new CreateOrderRequest(1L, List.of(
                new OrderItemRequest(100L, 1),
                new OrderItemRequest(100L, 4))));

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(5));
        assertThat(keyboard.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void createOrder_rejectsWhenStockIsInsufficient() {
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(products.findByIdForUpdate(200L)).thenReturn(Optional.of(mouse));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(1L,
                List.of(new OrderItemRequest(200L, 6)))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("MS-1");

        assertThat(mouse.getStockQuantity()).isEqualTo(5);
        verify(orders, never()).save(any());
    }

    @Test
    void createOrder_rejectsUnknownCustomer() {
        when(customers.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(99L,
                List.of(new OrderItemRequest(100L, 1)))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer");
    }

    @Test
    void cancellingOrder_restoresStock() {
        CustomerOrder order = new CustomerOrder(customer);
        order.addItem(new OrderItem(keyboard, 3));
        order.recalculateTotal();
        keyboard.setStockQuantity(7); // 3 already reserved

        when(orders.findById(5L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.of(keyboard));
        when(orders.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = orderService.updateStatus(5L, OrderStatus.CANCELLED);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(keyboard.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void invalidStatusTransition_isRejected() {
        CustomerOrder order = new CustomerOrder(customer);
        order.setStatus(OrderStatus.DELIVERED);
        when(orders.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(5L, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(orders, never()).saveAndFlush(any());
    }
}
