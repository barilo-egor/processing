package net.rcetech.support.service;

import net.rcetech.meta.Profiles;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;

@Service
@Profile(Profiles.TEST_MERCHANT_HISTORY)
public class MerchantHistoryServiceStub implements MerchantHistoryService {

    @Override
    public List<MerchantHistoryResponse> getHistory(MerchantHistoryFilter filter, Pageable pageable) {
        return List.of(
                new MerchantHistoryResponse(
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
