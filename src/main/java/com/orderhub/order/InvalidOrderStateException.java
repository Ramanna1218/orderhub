package com.orderhub.order;

import com.orderhub.common.exception.ConflictException;

public class InvalidOrderStateException extends ConflictException {

    public InvalidOrderStateException(OrderStatus from, OrderStatus to) {
        super("Cannot change order status from %s to %s".formatted(from, to));
    }
}
