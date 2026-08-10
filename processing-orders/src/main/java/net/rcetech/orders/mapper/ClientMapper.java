package net.rcetech.orders.mapper;

import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.grpc.generated.GetClientByIdResponseGrpc;
import net.rcetech.orders.dto.ClientDTO;
import net.rcetech.orders.enums.ClientStatus;

import java.time.Instant;

@Component
public class ClientMapper {

    public ClientDTO clientByResponseToDTO(GetClientByIdResponseGrpc response) {
        ClientDTO dto = new ClientDTO();
        dto.setId(response.getId());
        dto.setUsername(response.getUsername().isEmpty() ? null : response.getUsername());
        dto.setApiKeyPreview(response.getApiKeyPreview().isEmpty() ? null : response.getApiKeyPreview());
        if (response.hasRegisteredAt() && !response.getRegisteredAt().equals(Timestamp.getDefaultInstance())) {
            dto.setRegisteredAt(timestampToInstant(response.getRegisteredAt()));
        } else {
            dto.setRegisteredAt(null);
        }
        if (!response.getStatus().isEmpty()) {
            dto.setStatus(ClientStatus.valueOf(response.getStatus()));
        } else {
            dto.setStatus(null);
        }
        dto.setCallbackUrl(response.getCallbackUrl().isEmpty() ? null : response.getCallbackUrl());
        dto.setOrderTimeoutSeconds(response.getOrderTimeoutSeconds() == 0 ? null : response.getOrderTimeoutSeconds());
        return dto;
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

}
