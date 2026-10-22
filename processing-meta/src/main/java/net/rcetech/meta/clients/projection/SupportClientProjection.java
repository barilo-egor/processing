package net.rcetech.meta.clients.projection;

import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.serialize.InstantToMillisSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface SupportClientProjection {
    UUID getId();
    String getUsername();
    @JsonSerialize(using = InstantToMillisSerializer.class)
    Instant getRegisteredAt();
    String getCallbackUrl();
    Integer getOrderTimeoutSeconds();
    BigDecimal getCommissionPercent();
    Integer getBalance();
    Set<RequestMethod> getMethods();
    String getComment();
    Integer getMinWithdrawalAmount();
}
