package com.bardaghji.erpmigration.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CanonicalOrder(String orderId,
                             String customerId,
                             LocalDate orderDate,
                             BigDecimal totalAmount,
                             String currency,
                             OrderStatus status,
                             List<CanonicalOrderItem> items) {

    public CanonicalOrder {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(orderDate, "orderDate");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        Set<Integer> lineNumbers = new HashSet<>();
        for (CanonicalOrderItem item : items) {
            Objects.requireNonNull(item, "items must not contain null");
            if (!lineNumbers.add(item.lineNumber())) {
                throw new IllegalArgumentException("duplicate lineNumber " + item.lineNumber());
            }
        }

        items = items.stream()
                .sorted(Comparator.comparingInt(CanonicalOrderItem::lineNumber))
                .toList();
    }
}