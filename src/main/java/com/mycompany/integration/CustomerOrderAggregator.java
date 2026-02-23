package com.mycompany.integration;

import com.mycompany.integration.model.OrderRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AGG1: 개별 OrderRequest → CustomerBatch Map 누적
 * correlationKey: customerId
 * output: {customerId, customerTier, orders:[{orderId, orderType, customerId, quantity},...]}
 */
@ApplicationScoped
@Named("customerOrderAggregator")
public class CustomerOrderAggregator implements AggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderAggregator.class);

    @Override
    @SuppressWarnings("unchecked")
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        OrderRequest order = newExchange.getIn().getBody(OrderRequest.class);

        Map<String, Object> batch;
        if (oldExchange == null) {
            // 첫 번째 메시지: CustomerBatch 초기화
            batch = new HashMap<>();
            batch.put("customerId", order.getCustomerId());
            batch.put("customerTier", deriveCustomerTier(order.getCustomerId()));
            batch.put("orders", new ArrayList<>());
            log.debug("AGG1: new CustomerBatch for customerId={}", order.getCustomerId());
        } else {
            batch = oldExchange.getIn().getBody(Map.class);
        }

        // 주문 Map 생성 후 CustomerBatch의 orders 리스트에 추가
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("orderId", order.getOrderId());
        orderMap.put("orderType", order.getOrderType());
        orderMap.put("customerId", order.getCustomerId());
        orderMap.put("quantity", order.getQuantity() != null ? order.getQuantity() : 0);

        ((List<Map<String, Object>>) batch.get("orders")).add(orderMap);

        List<Map<String, Object>> orders = (List<Map<String, Object>>) batch.get("orders");
        log.debug("AGG1: accumulated {}/{} orders for customerId={}", orders.size(), order.getCustomerId());

        if (oldExchange == null) {
            newExchange.getIn().setBody(batch);
            return newExchange;
        } else {
            oldExchange.getIn().setBody(batch);
            return oldExchange;
        }
    }

    private String deriveCustomerTier(String customerId) {
        if (customerId == null) return "STANDARD";
        String upper = customerId.toUpperCase();
        if (upper.startsWith("VIP")) return "VIP";
        if (upper.startsWith("GOLD")) return "GOLD";
        return "STANDARD";
    }
}
