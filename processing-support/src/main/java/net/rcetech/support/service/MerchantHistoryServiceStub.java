package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponseDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;

@Service
@Profile("merchant-history-stub")
public class MerchantHistoryServiceStub implements MerchantHistoryService {

    @Override
    public List<MerchantHistoryResponseDTO> getHistory(MerchantHistoryFilter filter, Pageable pageable) {
        return List.of(
                new MerchantHistoryResponseDTO(
                        "op-1",
                        "actor-1",
                        "processing",
                        Instant.now(),
                        Merchant.ALFA_TEAM,
                        "order-1",
                        10000,
                        10000,
                        "CARD",
                        "1234567812345678"
                )
        );
    }

}
