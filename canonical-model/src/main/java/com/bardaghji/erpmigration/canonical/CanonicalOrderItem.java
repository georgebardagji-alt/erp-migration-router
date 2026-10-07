package com.bardaghji.erpmigration.canonical;

import java.math.BigDecimal;

public class CanonicalOrderItem {
    public int lineNumber;
    public String sku;
    public BigDecimal quantity;
    public String unit;

    public CanonicalOrderItem(int lineNumber, String sku, BigDecimal quantity, String unit) {
        this.lineNumber = lineNumber;
        this.sku = sku;
        this.quantity = quantity;
        this.unit = unit;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
