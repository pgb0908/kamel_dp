package com.mycompany.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.integration.model.OrderRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
@Named("orderFileParser")
public class OrderFileParser {

    private static final Logger log = LoggerFactory.getLogger(OrderFileParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * JSON 문자열을 List<OrderRequest>로 파싱합니다.
     * 배열([{...}]) 또는 단건({...}) 모두 처리합니다.
     */
    public List<OrderRequest> parseOrders(Exchange exchange) throws Exception {
        String json = exchange.getIn().getBody(String.class);
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);

        if (json == null || json.isBlank()) {
            log.warn("Empty file received: {}", fileName);
            return Collections.emptyList();
        }

        String trimmed = json.trim();
        List<OrderRequest> orders;

        if (trimmed.startsWith("[")) {
            orders = objectMapper.readValue(trimmed, new TypeReference<List<OrderRequest>>() {});
        } else {
            OrderRequest single = objectMapper.readValue(trimmed, OrderRequest.class);
            orders = Collections.singletonList(single);
        }

        log.debug("Parsed {} order(s) from file: {}", orders.size(), fileName);
        return orders;
    }
}
