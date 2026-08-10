package net.rcetech.clients.mapper;

import net.rcetech.clients.dto.WithdrawalRequestDTO;
import net.rcetech.clients.entity.WithdrawalRequest;
import net.rcetech.clients.enums.WithdrawalRequestStatus;
import net.rcetech.meta.clients.dto.CreateWithdrawalRequestDTO;
import net.rcetech.meta.clients.dto.UpdateWithdrawalRequestDTO;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalMapper {

    public WithdrawalRequestDTO toInternalDto(CreateWithdrawalRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return WithdrawalRequestDTO.builder()
                .clientId(dto.clientId())
                .amount(dto.amount())
                .wallet(dto.wallet())
                .comment(dto.comment())
                .build();
    }

    public WithdrawalRequestDTO toInternalDto(UpdateWithdrawalRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return WithdrawalRequestDTO.builder()
                .id(dto.id())
                .wallet(dto.wallet())
                .comment(dto.comment())
                .build();
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

}
