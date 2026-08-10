package net.rcetech.clientsapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.rcetech.clientsapi.dto.ClientResponseDTO;
import net.rcetech.clientsapi.dto.CreateClientDTO;
import net.rcetech.clientsapi.dto.CreateClientResponseDTO;
import net.rcetech.clientsapi.dto.CreateSignatureDTO;

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
