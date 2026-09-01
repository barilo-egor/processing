package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;
import java.util.UUID;

@Service
@Deprecated(forRemoval = true)
public class MerchantConfigServiceStub implements MerchantConfigService{
    @Override
    public List<MerchantConfigResponseDTO> findAll(UUID clientId) {
        return List.of(
                new MerchantConfigResponseDTO(1L, true, Merchant.ALFA_TEAM, 10000, 100),
                new MerchantConfigResponseDTO(2L, true, Merchant.FIAT_CUT, 4500, 500),
                new MerchantConfigResponseDTO(3L, false, Merchant.EXTASY_PAY, 10000, 100),
                new MerchantConfigResponseDTO(4L, true, Merchant.ASGARD, 15000, 10000)
        );
    }

    @Override
    public MerchantConfigResponseDTO update(Long id, MerchantConfigUpdateDTO updateDTO) {
        return new MerchantConfigResponseDTO(id, true, Merchant.ALFA_TEAM, 10000, 100);
    }
}
