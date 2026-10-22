package net.rcetech.meta.serialize;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.Instant;

public class InstantToMillisSerializer extends ValueSerializer<Instant> {

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeNumber(value.toEpochMilli());
    }
}
