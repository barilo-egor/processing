package net.rcetech.meta.serialize;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.Instant;

public class MillisToInstantDeserializer extends ValueDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return Instant.ofEpochMilli(p.getLongValue());
    }
}
