package com.mycompany.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRequest {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("notes")
    private String notes;

    public OrderRequest() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return "OrderRequest{orderId='" + orderId + "', orderType='" + orderType
                + "', customerId='" + customerId + "', quantity=" + quantity + "}";
    }
}
