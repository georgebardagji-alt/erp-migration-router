package com.bardaghji.erpmigration.canonical;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CanonicalSerializerTest {
    private final CanonicalSerializer canonicalSerializer = new CanonicalSerializer();

    private static CanonicalOrderItem itemSample (int lineNumber, String quantity){
        return new CanonicalOrderItem(lineNumber,
                "0101010",
                new BigDecimal(quantity),
                "EA");
    }

    private static CanonicalOrder orderSample (String totalAmount, List<CanonicalOrderItem> items){
        return new CanonicalOrder("1020304",
                "CUST1",
                LocalDate.of(2026, 9, 20),
                new BigDecimal(totalAmount),
                "EUR",
                OrderStatus.OPEN,
                items);
    }

    @Test
    public void sameOrder_ProducesSameOrderHash() throws NoSuchAlgorithmException {
        CanonicalOrder firstOrder = orderSample("10", List.of(itemSample(10, "3")));
        CanonicalOrder secondOrder = orderSample("10", List.of(itemSample(10, "3")));

        String firstOrderJson = canonicalSerializer.toJson(firstOrder);
        String secondOrderJson = canonicalSerializer.toJson(secondOrder);

        assertThat(firstOrderJson).isEqualTo(secondOrderJson);
        assertThat(canonicalSerializer.sha256Hex(firstOrderJson)).isEqualTo(canonicalSerializer.sha256Hex(secondOrderJson));
    }

    @Test
    public void itemOrder_doesNotChangeJson() {
        CanonicalOrder ascending  = orderSample("125.00", List.of(itemSample(10, "2"), itemSample(20, "3")));
        CanonicalOrder descending = orderSample("125.00", List.of(itemSample(20, "3"), itemSample(10, "2")));

        assertThat(canonicalSerializer.toJson(descending)).isEqualTo(canonicalSerializer.toJson(ascending));
    }

    // 3 — trailingZeros_doNotChangeJson()
    @Test
    public void trailingZeros_doNotChangeJson() throws NoSuchAlgorithmException {
        CanonicalOrder firstOrder = orderSample("10", List.of(itemSample(10, "3.000")));
        CanonicalOrder secondOrder = orderSample("10", List.of(itemSample(10, "3")));

        String firstOrderJson = canonicalSerializer.toJson(firstOrder);
        String secondOrderJson = canonicalSerializer.toJson(secondOrder);

        assertThat(firstOrderJson).isEqualTo(secondOrderJson);
        assertThat(canonicalSerializer.sha256Hex(firstOrderJson)).isEqualTo(canonicalSerializer.sha256Hex(secondOrderJson));

    }
    // 4 — roundTrip_returnsEqualOrder()
    @Test
    public void roundTrip_returnsEqualOrder() {
        CanonicalOrder original = orderSample("125.00", List.of(itemSample(10, "2.000"), itemSample(20, "3")));

        String json = canonicalSerializer.toJson(original);
        CanonicalOrder restored = canonicalSerializer.fromJson(json);

        assertThat(restored)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(original);
    }
    // 5 — differentTotal_producesDifferentHash()
    @Test
    public void differentTotal_producesDifferentHash() throws NoSuchAlgorithmException {
        CanonicalOrder firstOrder = orderSample("10", List.of(itemSample(10, "3")));
        CanonicalOrder secondOrder = orderSample("13", List.of(itemSample(10, "3")));

        String firstOrderJson = canonicalSerializer.toJson(firstOrder);
        String secondOrderJson = canonicalSerializer.toJson(secondOrder);

        assertThat(firstOrderJson).isNotEqualTo(secondOrderJson);
        assertThat(canonicalSerializer.sha256Hex(firstOrderJson)).isNotEqualTo(canonicalSerializer.sha256Hex(secondOrderJson));
    }

    //6 SHA256Hex matches known vector
    @Test
    public void sha256hex_matchesKnownVector(){
        assertThat(canonicalSerializer.sha256Hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
