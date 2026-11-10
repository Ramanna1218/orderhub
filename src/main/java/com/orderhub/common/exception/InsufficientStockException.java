package com.orderhub.common.exception;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for product %s: requested %d, available %d".formatted(sku, requested, available));
    }
}
