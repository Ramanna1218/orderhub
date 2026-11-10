package com.orderhub.order;

import com.orderhub.common.dto.PageResponse;
import com.orderhub.common.exception.InsufficientStockException;
import com.orderhub.common.exception.ResourceNotFoundException;
import com.orderhub.customer.Customer;
import com.orderhub.customer.CustomerRepository;
import com.orderhub.order.OrderDtos.CreateOrderRequest;
import com.orderhub.order.OrderDtos.OrderItemRequest;
import com.orderhub.order.OrderDtos.OrderResponse;
import com.orderhub.product.Product;
import com.orderhub.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;

    public OrderService(OrderRepository orders, CustomerRepository customers, ProductRepository products) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
    }

    /**
     * Places an order atomically: validates the customer, locks each product row, checks and
     * decrements stock, snapshots prices, and computes the total. Any failure rolls back everything.
     */
    public OrderResponse create(CreateOrderRequest request) {
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        // Merge duplicate lines; TreeMap gives a stable lock order (by product id) to avoid deadlocks.
        Map<Long, Integer> quantityByProduct = new TreeMap<>();
        for (OrderItemRequest line : request.items()) {
            quantityByProduct.merge(line.productId(), line.quantity(), Integer::sum);
        }

        CustomerOrder order = new CustomerOrder(customer);
        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Product product = products.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", entry.getKey()));
            int quantity = entry.getValue();
            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(product.getSku(), quantity, product.getStockQuantity());
            }
            product.setStockQuantity(product.getStockQuantity() - quantity);
            order.addItem(new OrderItem(product, quantity));
        }
        order.recalculateTotal();
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(OrderStatus status, Long customerId, Pageable pageable) {
        Page<CustomerOrder> page;
        if (customerId != null && status != null) {
            page = orders.findByCustomerIdAndStatus(customerId, status, pageable);
        } else if (customerId != null) {
            page = orders.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            page = orders.findByStatus(status, pageable);
        } else {
            page = orders.findAll(pageable);
        }
        return PageResponse.from(page.map(OrderResponse::from));
    }

    /** Moves an order through its lifecycle; cancelling returns reserved stock. */
    public OrderResponse updateStatus(Long id, OrderStatus next) {
        CustomerOrder order = find(id);
        if (!order.getStatus().canTransitionTo(next)) {
            throw new InvalidOrderStateException(order.getStatus(), next);
        }
        if (next == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                Product product = products.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", item.getProduct().getId()));
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
        }
        order.setStatus(next);
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    private CustomerOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }
}
