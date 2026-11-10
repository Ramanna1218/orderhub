package com.orderhub.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "may contain only digits, spaces and + ( ) -")
            String phone) {
    }

    public record CustomerResponse(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            Instant createdAt,
            Instant updatedAt) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                    c.getPhone(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
