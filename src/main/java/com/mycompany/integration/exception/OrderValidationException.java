package com.mycompany.integration.exception;

public class OrderValidationException extends RuntimeException {

    private final String orderId;
    private final String field;

    public OrderValidationException(String message) {
        super(message);
        this.orderId = null;
        this.field = null;
    }

    public OrderValidationException(String orderId, String field, String message) {
        super(message);
        this.orderId = orderId;
        this.field = field;
    }

    public String getOrderId() { return orderId; }
    public String getField() { return field; }
}
