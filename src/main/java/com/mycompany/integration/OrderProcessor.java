package com.mycompany.integration;

import com.mycompany.integration.model.OrderRequest;
import com.mycompany.integration.model.OrderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@ApplicationScoped
@Named("orderProcessor")
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    public OrderResult processExpress(Exchange exchange) {
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);
        log.debug("Processing EXPRESS order: {}", order.getOrderId());

        OrderResult result = OrderResult.success(order, Instant.now().toString());
        result.setProcessingNode(exchange.getIn().getHeader("processingNode", String.class));
        result.setCustomerTier(exchange.getIn().getHeader("customerTier", String.class));
        result.setMessage("EXPRESS order processed with priority handling");

        // Set filename header for file output
        exchange.getIn().setHeader(Exchange.FILE_NAME, "express-" + order.getOrderId() + "-" + System.currentTimeMillis() + ".json");

        return result;
    }

    public OrderResult processStandard(Exchange exchange) {
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);
        log.debug("Processing STANDARD order: {}", order.getOrderId());

        OrderResult result = OrderResult.success(order, Instant.now().toString());
        result.setProcessingNode(exchange.getIn().getHeader("processingNode", String.class));
        result.setCustomerTier(exchange.getIn().getHeader("customerTier", String.class));
        result.setMessage("STANDARD order processed successfully");

        // Set filename header for file output
        exchange.getIn().setHeader(Exchange.FILE_NAME, "standard-" + order.getOrderId() + "-" + System.currentTimeMillis() + ".json");

        return result;
    }
}
