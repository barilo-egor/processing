package net.rcetech.support.service;

import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MerchantHistoryService {

    /**
     * Получает историю мерчантов по фильтру и параметрам пагинации.
     *
     * @param filter   фильтр поиска
     * @param pageable параметры пагинации и сортировки
     * @return страница записей истории мерчанта
     */
    Page<MerchantHistoryResponse> getHistory(MerchantHistoryFilter filter, Pageable pageable);

}
