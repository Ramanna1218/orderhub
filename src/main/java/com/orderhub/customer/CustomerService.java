package com.orderhub.customer;

import com.orderhub.common.dto.PageResponse;
import com.orderhub.common.exception.ConflictException;
import com.orderhub.common.exception.DuplicateResourceException;
import com.orderhub.common.exception.ResourceNotFoundException;
import com.orderhub.customer.CustomerDtos.CustomerRequest;
import com.orderhub.customer.CustomerDtos.CustomerResponse;
import com.orderhub.order.OrderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final OrderRepository orders;

    public CustomerService(CustomerRepository customers, OrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    public CustomerResponse create(CustomerRequest request) {
        String email = normalize(request.email());
        if (customers.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Customer with email '%s' already exists".formatted(email));
        }
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Pageable pageable) {
        return PageResponse.from(customers.findAll(pageable).map(CustomerResponse::from));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        String email = normalize(request.email());
        if (!customer.getEmail().equalsIgnoreCase(email) && customers.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Customer with email '%s' already exists".formatted(email));
        }
        apply(customer, request);
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    public void delete(Long id) {
        Customer customer = find(id);
        if (orders.existsByCustomerId(id)) {
            throw new ConflictException("Customer %d has orders and cannot be deleted".formatted(id));
        }
        customers.delete(customer);
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(normalize(request.email()));
        customer.setPhone(request.phone());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
