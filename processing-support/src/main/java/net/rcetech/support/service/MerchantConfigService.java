package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;

import java.util.List;

public interface MerchantConfigService {

    /**
     * Получение конфигураций на каждого мерчанта, перечисленного в {@link tgb.cryptoexchange.commons.enums.Merchant}.
     * @return список конфигураций на всех мерчантов, отсортированных в порядке очереди по возрастанию
     */
    List<MerchantConfigResponseDTO> findAll();

    /**
     * Обновление конфигурации мерчанта. Обновляются только поля, не равные null.
     * @param id идентификатор конфигурации
     * @param updateDTO объект, содержащий обновляемые поля
     * @return обновленную конфигурацию
     */
    MerchantConfigResponseDTO update(Long id, MerchantConfigUpdateDTO updateDTO);
}
