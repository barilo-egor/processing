package net.rcetech.meta.clients.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateClientDTO;
import net.rcetech.meta.clients.dto.CreateClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateSignatureDTO;

public interface ClientApi {

    /**
     * Создает нового клиента.
     */
    CreateClientResponseDTO createClient(CreateClientDTO createClientDTO);

    /**
     * Возвращает клиента по API-ключу.
     */
    ClientResponseDTO getClientByApiKey(@NotBlank String apiKey);

    /**
     * Возвращает клиента по его идентификатору.
     */
    ClientResponseDTO getClientById(@NotNull @Positive Long id);

    /**
     * Создает цифровую подпись для переданных данных.
     */
    String createSignature(@Valid CreateSignatureDTO dto);

}
