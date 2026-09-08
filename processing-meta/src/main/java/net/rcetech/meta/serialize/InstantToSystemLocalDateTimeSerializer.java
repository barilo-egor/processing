package net.rcetech.meta.serialize;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class InstantToSystemLocalDateTimeSerializer extends ValueSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeString(  LocalDateTime.ofInstant(value, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
    }
}
