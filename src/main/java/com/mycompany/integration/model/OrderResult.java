package com.mycompany.integration.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderResult {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("processingNode")
    private String processingNode;

    @JsonProperty("customerTier")
    private String customerTier;

    @JsonProperty("processedAt")
    private String processedAt;

    @JsonProperty("message")
    private String message;

    public OrderResult() {}

    public static OrderResult success(OrderRequest request, String processedAt) {
        OrderResult result = new OrderResult();
        result.orderId = request.getOrderId();
        result.status = "SUCCESS";
        result.orderType = request.getOrderType();
        result.customerId = request.getCustomerId();
        result.quantity = request.getQuantity();
        result.processedAt = processedAt;
        result.message = "Order processed successfully";
        return result;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getProcessingNode() { return processingNode; }
    public void setProcessingNode(String processingNode) { this.processingNode = processingNode; }

    public String getCustomerTier() { return customerTier; }
    public void setCustomerTier(String customerTier) { this.customerTier = customerTier; }

    public String getProcessedAt() { return processedAt; }
    public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
