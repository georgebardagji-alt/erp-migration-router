package com.bardaghji.erpmigration.canonical;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class CanonicalSerializer {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .addModule(new SimpleModule("canonical-decimal").addSerializer(BigDecimal.class, new PlainBigDecimalSerializer()))
            .build();

    public String toJson(CanonicalOrder order){
        return objectMapper.writeValueAsString(order);
    }

    public CanonicalOrder fromJson(String jsonOrder){
        return objectMapper.readValue(jsonOrder, CanonicalOrder.class);
    }

    public String sha256Hex(String order) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(order.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
