package com.mycompany.integration;

import com.mycompany.integration.model.OrderRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Instant;

@ApplicationScoped
@Named("orderEnricher")
public class OrderEnricher {

    private static final Logger log = LoggerFactory.getLogger(OrderEnricher.class);

    private static final String HOSTNAME;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-node";
        }
        HOSTNAME = host;
    }

    public void enrich(Exchange exchange) {
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);

        // 처리 노드 정보
        exchange.getIn().setHeader("processingNode", HOSTNAME);
        exchange.getIn().setHeader("receivedAt", Instant.now().toString());

        // 고객 등급 결정
        String tier = determineCustomerTier(order.getCustomerId());
        exchange.getIn().setHeader("customerTier", tier);

        log.debug("Enriched order {} — node={}, tier={}", order.getOrderId(), HOSTNAME, tier);
    }

    private String determineCustomerTier(String customerId) {
        if (customerId == null) return "STANDARD";
        String upper = customerId.toUpperCase();
        if (upper.startsWith("VIP")) return "VIP";
        if (upper.startsWith("GOLD")) return "GOLD";
        return "STANDARD";
    }
}
