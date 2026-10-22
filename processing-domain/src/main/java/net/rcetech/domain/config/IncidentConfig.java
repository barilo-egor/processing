package net.rcetech.domain.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.rcetech.meta.incidents.IncidentEvent;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class IncidentConfig {

    public static final String INCIDENT_OBJECT_MAPPER = "incidentObjectMapper";

    @Bean(name = INCIDENT_OBJECT_MAPPER, defaultCandidate = false)
    public ObjectMapper incidentObjectMapper() {
        return JsonMapper.builder()
                .addModule(new SimpleModule().addSerializer(OrderStatus.class, new OrderStatusDescriptionSerializer()))
                .addMixIn(IncidentEvent.class, IncidentEventMixIn.class)
                .build();
    }

    @JsonIgnoreProperties("errorTimestamp")
    private interface IncidentEventMixIn {
    }

    private static class OrderStatusDescriptionSerializer extends ValueSerializer<OrderStatus> {

        @Override
        public void serialize(OrderStatus value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.getDescription());
        }
    }
}
