package com.bardaghji.erpmigration.canonical;

import java.math.BigDecimal;
import java.util.Objects;

public record CanonicalOrderItem(int lineNumber, String sku, BigDecimal quantity, String unit) {

    public CanonicalOrderItem {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("lineNumber must be > 0, got " + lineNumber);
        }
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, got " + quantity);
        }
        Objects.requireNonNull(unit, "unit");
    }
}