package net.rcetech.meta.clients.projection;

public interface ClientProfileProjection extends ClientProjection {
    Integer getBalance();
    Integer getReservedAmount();
}
