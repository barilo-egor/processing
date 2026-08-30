package net.rcetech.meta.clients.dto;

import net.rcetech.meta.clients.ClientStatus;

public record UpdateClientDTO(ClientStatus status, Integer orderTimeoutSeconds, String callbackUrl) {}
