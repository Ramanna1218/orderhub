package com.orderhub.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

    Page<CustomerOrder> findByCustomerIdAndStatus(Long customerId, OrderStatus status, Pageable pageable);

    boolean existsByCustomerId(Long customerId);
}
