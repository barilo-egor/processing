package net.rcetech.clients.mapper;

import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import net.rcetech.clients.dto.ClientDTO;
import net.rcetech.clients.dto.GeneratedKeys;
import net.rcetech.clients.entity.Client;
import net.rcetech.grpc.generated.CreateClientGrpc;
import net.rcetech.grpc.generated.CreateClientResponseGrpc;
import net.rcetech.grpc.generated.GetClientByApiKeyResponseGrpc;
import net.rcetech.grpc.generated.GetClientByIdResponseGrpc;

import java.time.Instant;
import java.util.Objects;

@Component
public class ClientMapper {

    public ClientDTO toDTO(CreateClientGrpc client) {
        return ClientDTO.builder().username(client.getUsername()).password(client.getPassword()).build();
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

    public CreateClientResponseGrpc createClientResponseGrpc(ClientDTO clientDTO) {
        return CreateClientResponseGrpc.newBuilder()
                .setUsername(Objects.requireNonNullElse(clientDTO.getUsername(), ""))
                .setApiKey(Objects.requireNonNullElse(clientDTO.getApiKey(), ""))
                .setSecret(Objects.requireNonNullElse(clientDTO.getSecret(), ""))
                .setRegisteredAt(clientDTO.getRegisteredAt() != null
                        ? instantToTimestamp(clientDTO.getRegisteredAt())
                        : Timestamp.getDefaultInstance())
                .setStatus(clientDTO.getStatus() != null ? clientDTO.getStatus().name() : "")
                .setCallbackUrl(Objects.requireNonNullElse(clientDTO.getCallbackUrl(), ""))
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

    public GetClientByApiKeyResponseGrpc getClientByApiKeyResponseGrpc(ClientDTO clientDTO) {
        return GetClientByApiKeyResponseGrpc.newBuilder()
                .setId(clientDTO.getId())
                .setUsername(clientDTO.getUsername())
                .setSecret(clientDTO.getSecret())
                .setApiKeyPreview(clientDTO.getApiKeyPreview())
                .setRegisteredAt(instantToTimestamp(clientDTO.getRegisteredAt()))
                .setStatus(clientDTO.getStatus().name())
                .setCallbackUrl(Objects.requireNonNullElse(clientDTO.getCallbackUrl(), ""))
                .setOrderTimeoutSeconds(clientDTO.getOrderTimeoutSeconds())
                .build();
    }

    public GetClientByIdResponseGrpc getClientByIdResponseGrpc(ClientDTO clientDTO) {
        return GetClientByIdResponseGrpc.newBuilder()
                .setId(clientDTO.getId())
                .setUsername(Objects.requireNonNullElse(clientDTO.getUsername(), ""))
                .setApiKeyPreview(Objects.requireNonNullElse(clientDTO.getApiKeyPreview(), ""))
                .setRegisteredAt(clientDTO.getRegisteredAt() != null
                        ? instantToTimestamp(clientDTO.getRegisteredAt())
                        : Timestamp.getDefaultInstance())
                .setStatus(clientDTO.getStatus() != null ? clientDTO.getStatus().name() : "")
                .setCallbackUrl(Objects.requireNonNullElse(clientDTO.getCallbackUrl(), ""))
                .setOrderTimeoutSeconds(
                        clientDTO.getOrderTimeoutSeconds() != null ? clientDTO.getOrderTimeoutSeconds() : 0)
                .build();
    }

    private Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

}
