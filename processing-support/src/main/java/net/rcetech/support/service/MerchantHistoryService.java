package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MerchantHistoryService {

    /**
     * Получает историю мерчантов по фильтру и параметрам пагинации.
     *
     * @param filter   фильтр поиска
     * @param pageable параметры пагинации и сортировки
     * @return список записей истории мерчанта
     */
    List<MerchantHistoryResponseDTO> getHistory(MerchantHistoryFilter filter, Pageable pageable);

}
