package com.bardaghji.erpmigration.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class CanonicalOrder {

    public String orderId;
    public String customerId;
    public LocalDate orderDate;
    public BigDecimal totalAmount;
    public String currency;
    public OrderStatus status;
    public List<CanonicalOrderItem> items;

    public CanonicalOrder(String orderId, String customerId, LocalDate orderDate, BigDecimal totalAmount, String currency, OrderStatus status, List<CanonicalOrderItem> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.items = items;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<CanonicalOrderItem> getItems() {
        return items;
    }

    public void setItems(List<CanonicalOrderItem> items) {
        this.items = items;
    }
}
