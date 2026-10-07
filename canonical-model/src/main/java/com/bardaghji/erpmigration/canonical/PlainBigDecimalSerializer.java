package com.bardaghji.erpmigration.canonical;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;

public class PlainBigDecimalSerializer extends ValueSerializer<BigDecimal> {
    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt){
        gen.writeString(value.stripTrailingZeros().toPlainString());
    }
}
