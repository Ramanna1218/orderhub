package com.orderhub.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PENDING, CONFIRMED, true",
            "PENDING, CANCELLED, true",
            "PENDING, SHIPPED, false",
            "CONFIRMED, SHIPPED, true",
            "CONFIRMED, CANCELLED, true",
            "SHIPPED, DELIVERED, true",
            "SHIPPED, CANCELLED, false",
            "DELIVERED, CANCELLED, false",
            "CANCELLED, PENDING, false"
    })
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
