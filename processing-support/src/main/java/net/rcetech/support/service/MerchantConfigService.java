package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;

import java.util.List;

public interface MerchantConfigService {

    List<MerchantConfigResponseDTO> findAll();

    void update(Long id, MerchantConfigUpdateDTO updateDTO);
}
