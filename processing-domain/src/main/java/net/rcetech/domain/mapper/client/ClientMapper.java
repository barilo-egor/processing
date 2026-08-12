package net.rcetech.domain.mapper.client;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.meta.clients.dto.*;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public CreateClientResponseDTO toCreateClientResponseDTO(ClientDTO dto) {
        return new CreateClientResponseDTO(
                dto.getUsername(),
                dto.getApiKey(),
                dto.getSecret(),
                dto.getRegisteredAt(),
                dto.getStatus() != null ? dto.getStatus().name() : null,
                dto.getCallbackUrl()
        );
    }

    public ClientResponseDTO toClientResponseDTO(ClientDTO dto) {
        if (dto == null) {
            return null;
        }
        return new ClientResponseDTO(
                dto.getId(),
                dto.getUsername(),
                dto.getSecret(),
                dto.getApiKeyPreview(),
                dto.getRegisteredAt(),
                dto.getStatus() != null ? dto.getStatus().name() : null,
                dto.getCallbackUrl(),
                dto.getOrderTimeoutSeconds()
        );
    }

    public ClientDTO toInternalDto(CreateClientDTO dto) {
        if (dto == null) {
            return null;
        }
        return ClientDTO.builder()
                .username(dto.username())
                .password(dto.password())
                .build();
    }

    public ClientDTO createdClientToDTO(Client client, GeneratedKeys generatedKeys) {
        return ClientDTO.builder()
                .id(client.getId())
                .username(client.getUsername())
                .apiKey(generatedKeys.key())
                .secret(generatedKeys.secret())
                .registeredAt(client.getRegisteredAt())
                .status(client.getStatus())
                .callbackUrl(client.getCallbackUrl())
                .build();
    }

    public ClientDTO getClientByApiKeyDTO(Client client, String decryptedSecret) {
        return ClientDTO.builder()
                .id(client.getId())
                .username(client.getUsername())
                .password(client.getPassword())
                .apiKey(client.getApiKey())
                .apiKeyPreview(client.getApiKeyPreview())
                .secret(decryptedSecret)
                .registeredAt(client.getRegisteredAt())
                .status(client.getStatus())
                .callbackUrl(client.getCallbackUrl())
                .orderTimeoutSeconds(client.getOrderTimeoutSeconds())
                .build();
    }

    public ClientDTO clientToDTO(Client client) {
        return ClientDTO.builder()
                .id(client.getId())
                .username(client.getUsername())
                .password(client.getPassword())
                .apiKey(client.getApiKey())
                .apiKeyPreview(client.getApiKeyPreview())
                .secret(client.getSecret())
                .registeredAt(client.getRegisteredAt())
                .status(client.getStatus())
                .callbackUrl(client.getCallbackUrl())
                .orderTimeoutSeconds(client.getOrderTimeoutSeconds())
                .build();
    }

}
