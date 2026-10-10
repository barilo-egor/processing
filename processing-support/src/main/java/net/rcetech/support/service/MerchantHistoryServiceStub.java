package net.rcetech.support.service;

import net.rcetech.meta.Profiles;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile(Profiles.TEST_MERCHANT_HISTORY)
public class MerchantHistoryServiceStub implements MerchantHistoryService {

    private final List<MerchantHistoryResponse> stubHistories;

    public MerchantHistoryServiceStub() {
        this.stubHistories = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            stubHistories.add(new MerchantHistoryResponse(
                    "op-" + i,
                    "actor-" + (i % 3),
                    "processing",
                    Instant.ofEpochMilli(Instant.now().toEpochMilli() - (i * 10000)),
                    Merchant.ALFA_TEAM,
                    "merchant-order-id-" + i,
                    10000,
                    10000,
                    RequestMethod.values()[i % 2].name(),
                    i % 2 == 0 ? "T-Bank 1234 1234 1234 1234" : "АЛЬФА +7 892 240 50 12"
            ));
        }
    }

    @Override
    public List<MerchantHistoryResponse> getHistory(MerchantHistoryFilter filter, Pageable pageable) {
        return stubHistories;
    }

}
