package com.mycompany.integration;

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
 * AGG2: 분할된 order Map → TypeSummary Map 누적
 * correlationKey: customerId-orderType
 * output: {customerId, customerTier, orderType, count, totalQuantity, orderIds:[...]}
 */
@ApplicationScoped
@Named("typeGroupAggregator")
public class TypeGroupAggregator implements AggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(TypeGroupAggregator.class);

    @Override
    @SuppressWarnings("unchecked")
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Map<String, Object> orderMap = newExchange.getIn().getBody(Map.class);
        String customerId = newExchange.getIn().getHeader("customerId", String.class);
        String customerTier = newExchange.getIn().getHeader("customerTier", String.class);
        String orderType = newExchange.getIn().getHeader("orderType", String.class);

        Map<String, Object> summary;
        if (oldExchange == null) {
            // 첫 번째 메시지: TypeSummary 초기화
            summary = new HashMap<>();
            summary.put("customerId", customerId);
            summary.put("customerTier", customerTier);
            summary.put("orderType", orderType);
            summary.put("count", 0);
            summary.put("totalQuantity", 0);
            summary.put("orderIds", new ArrayList<>());
            log.debug("AGG2: new TypeSummary for {}-{}", customerId, orderType);
        } else {
            summary = oldExchange.getIn().getBody(Map.class);
        }

        // quantity 타입 안전 처리 (Number → int)
        Object quantityObj = orderMap.get("quantity");
        int quantity = quantityObj instanceof Number ? ((Number) quantityObj).intValue() : 0;

        summary.put("count", (Integer) summary.get("count") + 1);
        summary.put("totalQuantity", (Integer) summary.get("totalQuantity") + quantity);
        ((List<String>) summary.get("orderIds")).add((String) orderMap.get("orderId"));

        log.debug("AGG2: accumulated count={} totalQty={} for {}-{}",
                summary.get("count"), summary.get("totalQuantity"), customerId, orderType);

        if (oldExchange == null) {
            newExchange.getIn().setBody(summary);
            return newExchange;
        } else {
            oldExchange.getIn().setBody(summary);
            return oldExchange;
        }
    }
}
