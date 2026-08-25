package net.rcetech.meta.clients.dto;

import net.rcetech.meta.clients.ClientStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Форма фильтра клиентов в админ панели.
 */
public record ClientFilter(UUID id, String username, ClientStatus status, Instant from, Instant to) {
}
