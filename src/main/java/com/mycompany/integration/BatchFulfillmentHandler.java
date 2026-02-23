package com.mycompany.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * BatchReport 처리 및 fulfillment 라우팅 지원 Bean.
 * - prepareHeaders(): BatchReport에서 choice 분기용 헤더 추출
 * - processBulk/processVip/processStandard(): 각 fulfillment 처리 + CamelFileName 설정
 * - compensate(): 실패 시 보상 레코드 생성
 */
@ApplicationScoped
@Named("batchFulfillmentHandler")
public class BatchFulfillmentHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchFulfillmentHandler.class);

    /**
     * BatchReport Map에서 choice 분기용 헤더를 추출하여 설정합니다.
     * (batchId, customerId, customerTier, totalQuantity)
     */
    @SuppressWarnings("unchecked")
    public void prepareHeaders(Exchange exchange) {
        Map<String, Object> report = exchange.getIn().getBody(Map.class);
        exchange.getIn().setHeader("batchId", report.get("batchId"));
        exchange.getIn().setHeader("customerId", report.get("customerId"));
        exchange.getIn().setHeader("customerTier", report.get("customerTier"));
        exchange.getIn().setHeader("totalQuantity", report.get("totalQuantity"));

        log.debug("prepareHeaders: batchId={}, customerId={}, customerTier={}, totalQuantity={}",
                report.get("batchId"), report.get("customerId"),
                report.get("customerTier"), report.get("totalQuantity"));
    }

    /**
     * BULK fulfillment 처리 (totalQuantity > 100 경로).
     * 볼륨 할인을 적용한 대량 주문 처리 결과를 반환합니다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processBulk(Exchange exchange) {
        Map<String, Object> report = exchange.getIn().getBody(Map.class);
        String batchId = exchange.getIn().getHeader("batchId", String.class);
        String customerId = exchange.getIn().getHeader("customerId", String.class);

        log.debug("processBulk: batchId={}, customerId={}, totalQuantity={}",
                batchId, customerId, exchange.getIn().getHeader("totalQuantity"));

        Map<String, Object> result = new HashMap<>(report);
        result.put("fulfillmentType", "BULK");
        result.put("status", "FULFILLED");
        result.put("processedAt", Instant.now().toString());
        result.put("message", "Bulk fulfillment processed with volume discount applied");

        exchange.getIn().setHeader(Exchange.FILE_NAME,
                "bulk-" + batchId + "-" + customerId + "-" + System.currentTimeMillis() + ".json");

        return result;
    }

    /**
     * VIP fulfillment 처리 (customerTier == 'VIP' 경로).
     * 우선 처리 및 전담 서비스를 적용한 결과를 반환합니다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processVip(Exchange exchange) {
        Map<String, Object> report = exchange.getIn().getBody(Map.class);
        String batchId = exchange.getIn().getHeader("batchId", String.class);
        String customerId = exchange.getIn().getHeader("customerId", String.class);

        log.debug("processVip: batchId={}, customerId={}", batchId, customerId);

        Map<String, Object> result = new HashMap<>(report);
        result.put("fulfillmentType", "VIP");
        result.put("status", "FULFILLED");
        result.put("processedAt", Instant.now().toString());
        result.put("message", "VIP fulfillment processed with priority service and dedicated support");

        exchange.getIn().setHeader(Exchange.FILE_NAME,
                "vip-" + batchId + "-" + customerId + "-" + System.currentTimeMillis() + ".json");

        return result;
    }

    /**
     * STANDARD fulfillment 처리 (기본 경로).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processStandard(Exchange exchange) {
        Map<String, Object> report = exchange.getIn().getBody(Map.class);
        String batchId = exchange.getIn().getHeader("batchId", String.class);
        String customerId = exchange.getIn().getHeader("customerId", String.class);

        log.debug("processStandard: batchId={}, customerId={}", batchId, customerId);

        Map<String, Object> result = new HashMap<>(report);
        result.put("fulfillmentType", "STANDARD");
        result.put("status", "FULFILLED");
        result.put("processedAt", Instant.now().toString());
        result.put("message", "Standard fulfillment processed successfully");

        exchange.getIn().setHeader(Exchange.FILE_NAME,
                "standard-" + batchId + "-" + customerId + "-" + System.currentTimeMillis() + ".json");

        return result;
    }

    /**
     * 처리 실패 시 보상 레코드를 생성합니다 (Compensating Transaction 패턴).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> compensate(Exchange exchange) {
        Map<String, Object> report = exchange.getIn().getBody(Map.class);
        String batchId = exchange.getIn().getHeader("batchId", String.class);
        String customerId = exchange.getIn().getHeader("customerId", String.class);
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);

        log.warn("compensate: batchId={}, customerId={}, reason={}",
                batchId, customerId, cause != null ? cause.getMessage() : "unknown");

        Map<String, Object> compensation = new HashMap<>();
        compensation.put("batchId", batchId);
        compensation.put("customerId", customerId);
        compensation.put("status", "COMPENSATION_RECORDED");
        compensation.put("reason", cause != null ? cause.getMessage() : "Unknown error");
        compensation.put("originalReport", report);
        compensation.put("compensatedAt", Instant.now().toString());

        exchange.getIn().setHeader(Exchange.FILE_NAME,
                "compensation-" + batchId + "-" + customerId + "-" + System.currentTimeMillis() + ".json");

        return compensation;
    }
}
