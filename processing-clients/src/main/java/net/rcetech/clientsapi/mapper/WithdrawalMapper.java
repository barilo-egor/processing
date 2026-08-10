package net.rcetech.clientsapi.mapper;

import org.springframework.stereotype.Component;
import net.rcetech.clientsapi.dto.WithdrawalRequestDTO;
import net.rcetech.clientsapi.entity.WithdrawalRequest;
import net.rcetech.clientsapi.enums.WithdrawalRequestStatus;
import tgb.cryptoexchange.grpc.generated.CreateWithdrawalRequestGrpc;
import tgb.cryptoexchange.grpc.generated.UpdateWithdrawalRequestGrpc;

@Component
public class WithdrawalMapper {

    public WithdrawalRequestDTO createWithdrawalRequestGrpcToDTO(CreateWithdrawalRequestGrpc requestGrpc) {
        WithdrawalRequestDTO.WithdrawalRequestDTOBuilder builder = WithdrawalRequestDTO.builder()
                .clientId(requestGrpc.getClientId())
                .amount(requestGrpc.getAmount())
                .wallet(requestGrpc.getWallet());
        if (requestGrpc.hasComment()) {
            builder.comment(requestGrpc.getComment().getValue());
        }
        return builder.build();
    }

    public WithdrawalRequest requestDTOToEntity(WithdrawalRequestDTO requestDTO) {
        return WithdrawalRequest.builder()
                .clientId(requestDTO.getClientId())
                .amount(requestDTO.getAmount())
                .wallet(requestDTO.getWallet())
                .comment(requestDTO.getComment())
                .status(WithdrawalRequestStatus.NEW)
                .build();
    }

    public WithdrawalRequestDTO entityToDTO(WithdrawalRequest entity) {
        return WithdrawalRequestDTO.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .amount(entity.getAmount())
                .wallet(entity.getWallet())
                .comment(entity.getComment())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public WithdrawalRequestDTO updateWithdrawalRequestGrpcToDTO(UpdateWithdrawalRequestGrpc requestGrpc) {
        WithdrawalRequestDTO.WithdrawalRequestDTOBuilder builder = WithdrawalRequestDTO.builder();
        if (requestGrpc.hasComment()) {
            builder.comment(requestGrpc.getComment().getValue());
        }
        if (requestGrpc.hasWallet()) {
            builder.wallet(requestGrpc.getWallet().getValue());
        }
        return builder.build();
    }

}
