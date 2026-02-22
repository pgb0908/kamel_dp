package com.mycompany.integration;

import com.mycompany.integration.model.OrderRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Named("orderErrorHandler")
public class OrderErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderErrorHandler.class);

    /**
     * Dead Letter 아카이브 레코드를 생성합니다.
     */
    public Map<String, Object> createDeadLetterRecord(Exchange exchange) {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("archivedAt", Instant.now().toString());
        record.put("errorType", cause != null ? cause.getClass().getSimpleName() : "Unknown");
        record.put("errorMessage", cause != null ? cause.getMessage() : "No error message");

        String orderId = (order != null) ? order.getOrderId()
                : exchange.getIn().getHeader("orderId", String.class);
        record.put("orderId", orderId != null ? orderId : "UNKNOWN");

        if (order != null) {
            record.put("orderType", order.getOrderType());
            record.put("customerId", order.getCustomerId());
            record.put("quantity", order.getQuantity());
        }

        record.put("redeliveryCount", exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, 0, Integer.class));

        String fileName = "dead-letter-" + record.get("orderId") + "-" + System.currentTimeMillis() + ".json";
        exchange.getIn().setHeader(Exchange.FILE_NAME, fileName);

        log.warn("Dead letter record created for orderId={}, error={}", record.get("orderId"), record.get("errorMessage"));
        return record;
    }
}
