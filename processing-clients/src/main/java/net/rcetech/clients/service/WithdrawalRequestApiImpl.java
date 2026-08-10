package net.rcetech.clients.service;

import lombok.RequiredArgsConstructor;
import net.rcetech.clients.dto.WithdrawalRequestDTO;
import net.rcetech.clients.mapper.WithdrawalMapper;
import net.rcetech.meta.clients.dto.CreateWithdrawalRequestDTO;
import net.rcetech.meta.clients.dto.UpdateWithdrawalRequestDTO;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawalRequestApiImpl implements WithdrawalRequestApi {

    private final WithdrawalRequestService withdrawalRequestService;

    private final WithdrawalMapper mapper;

    @Override
    public void createWithdrawalRequest(CreateWithdrawalRequestDTO dto) {
        WithdrawalRequestDTO internalDto = mapper.toInternalDto(dto);
        withdrawalRequestService.saveWithdrawalRequest(internalDto);
    }

    @Override
    public void updateWithdrawalRequest(UpdateWithdrawalRequestDTO dto) {
        WithdrawalRequestDTO internalDto = mapper.toInternalDto(dto);
        withdrawalRequestService.updateWithdrawalRequest(dto.id(), internalDto);
    }

}
