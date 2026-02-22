package com.mycompany.integration;

import com.mycompany.integration.exception.OrderValidationException;
import com.mycompany.integration.model.OrderRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@ApplicationScoped
@Named("orderValidator")
public class OrderValidator {

    private static final Logger log = LoggerFactory.getLogger(OrderValidator.class);
    private static final Set<String> VALID_ORDER_TYPES = Set.of("EXPRESS", "STANDARD");

    public void validate(Exchange exchange) {
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);

        if (order == null) {
            throw new OrderValidationException(null, "body", "Request body is null or could not be parsed");
        }

        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            throw new OrderValidationException(order.getOrderId(), "orderId", "orderId is required");
        }

        if (order.getOrderType() == null || order.getOrderType().isBlank()) {
            throw new OrderValidationException(order.getOrderId(), "orderType", "orderType is required");
        }

        if (!VALID_ORDER_TYPES.contains(order.getOrderType().toUpperCase())) {
            throw new OrderValidationException(order.getOrderId(), "orderType",
                    "orderType must be one of: " + VALID_ORDER_TYPES + ", got: " + order.getOrderType());
        }

        if (order.getCustomerId() == null || order.getCustomerId().isBlank()) {
            throw new OrderValidationException(order.getOrderId(), "customerId", "customerId is required");
        }

        if (order.getQuantity() == null) {
            throw new OrderValidationException(order.getOrderId(), "quantity", "quantity is required");
        }

        // Normalize orderType to uppercase
        order.setOrderType(order.getOrderType().toUpperCase());
        exchange.getIn().setBody(order);

        // Set headers for downstream routing
        exchange.getIn().setHeader("orderId", order.getOrderId());
        exchange.getIn().setHeader("orderType", order.getOrderType());
        exchange.getIn().setHeader("customerId", order.getCustomerId());

        log.debug("Order validated: {}", order);
    }
}
