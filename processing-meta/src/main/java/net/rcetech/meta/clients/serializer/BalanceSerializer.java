package net.rcetech.meta.clients.serializer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BalanceSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeNumber(value.setScale(2, RoundingMode.FLOOR).stripTrailingZeros().toPlainString());
    }
}
