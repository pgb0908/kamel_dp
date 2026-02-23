package com.mycompany.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AGG3: TypeSummary Map → BatchReport Map 결합
 * correlationKey: customerId (body[customerId])
 * output: {batchId, customerId, customerTier, typeSummaries:[...], totalQuantity, totalOrders, createdAt}
 */
@ApplicationScoped
@Named("batchReportCombiner")
public class BatchReportCombiner implements AggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(BatchReportCombiner.class);

    @Override
    @SuppressWarnings("unchecked")
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Map<String, Object> typeSummary = newExchange.getIn().getBody(Map.class);
        String customerId = (String) typeSummary.get("customerId");
        String customerTier = (String) typeSummary.get("customerTier");

        Map<String, Object> report;
        if (oldExchange == null) {
            // 첫 번째 TypeSummary: BatchReport 초기화 (UUID batchId 생성)
            report = new HashMap<>();
            report.put("batchId", UUID.randomUUID().toString());
            report.put("customerId", customerId);
            report.put("customerTier", customerTier);
            report.put("typeSummaries", new ArrayList<>());
            report.put("totalQuantity", 0);
            report.put("totalOrders", 0);
            report.put("createdAt", Instant.now().toString());
            log.debug("AGG3: new BatchReport for customerId={}", customerId);
        } else {
            report = oldExchange.getIn().getBody(Map.class);
        }

        ((List<Map<String, Object>>) report.get("typeSummaries")).add(typeSummary);

        Object qtyObj = typeSummary.get("totalQuantity");
        Object cntObj = typeSummary.get("count");
        int qty = qtyObj instanceof Number ? ((Number) qtyObj).intValue() : 0;
        int cnt = cntObj instanceof Number ? ((Number) cntObj).intValue() : 0;

        report.put("totalQuantity", (Integer) report.get("totalQuantity") + qty);
        report.put("totalOrders", (Integer) report.get("totalOrders") + cnt);

        log.debug("AGG3: combined totalOrders={} totalQty={} for customerId={}",
                report.get("totalOrders"), report.get("totalQuantity"), customerId);

        if (oldExchange == null) {
            newExchange.getIn().setBody(report);
            return newExchange;
        } else {
            oldExchange.getIn().setBody(report);
            return oldExchange;
        }
    }
}
