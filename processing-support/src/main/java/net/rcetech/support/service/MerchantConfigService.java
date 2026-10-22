package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;

import java.util.List;
import java.util.UUID;

public interface MerchantConfigService {

    /**
     * Получение конфигураций на каждого мерчанта, перечисленного в {@link tgb.cryptoexchange.commons.enums.Merchant}.
     * @param clientId идентификатор клиента
     * @return список конфигураций на всех мерчантов, отсортированных в порядке очереди по возрастанию
     */
    List<MerchantConfigResponseDTO> findAll(UUID clientId);

    /**
     * Обновление конфигурации мерчанта. Обновляются только поля, не равные null.
     * @param id идентификатор конфигурации
     * @param updateDTO объект, содержащий обновляемые поля
     * @return обновленную конфигурацию
     */
    MerchantConfigResponseDTO update(Long id, MerchantConfigUpdateDTO updateDTO);
}
