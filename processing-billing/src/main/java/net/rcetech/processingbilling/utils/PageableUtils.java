package net.rcetech.processingbilling.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Утилитный класс для преобразования параметров пагинации и сортировки в объекты Spring Data.
 */
public class PageableUtils {

    private PageableUtils() {
    }

    /**
     * Создает объект {@link Pageable} на основе номера страницы, её размера и списка правил сортировки.
     *
     * @param page    номер целевой страницы (начиная с 0)
     * @param size    количество элементов на странице
     * @param sorters список строк сортировки в формате {@code "имяПоля,направление"}
     *                (например, {@code "createdAt,desc"}). По умолчанию используется ASC.
     * @return настроенный объект {@link Pageable} для передачи в репозиторий
     */
    public static Pageable createPageable(int page, int size, List<String> sorters) {
        int validatedPage = Math.max(page, 0);
        int validatedSize = (size < 1) ? 10 : size;
        if (CollectionUtils.isEmpty(sorters)) {
            return PageRequest.of(validatedPage, validatedSize);
        }
        List<Sort.Order> orders = sorters.stream()
                .map(sortStr -> {
                    String[] parts = sortStr.split(",");
                    String property = parts[0].trim();
                    Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                            ? Sort.Direction.DESC
                            : Sort.Direction.ASC;
                    return new Sort.Order(direction, property);
                })
                .toList();
        return PageRequest.of(validatedPage, validatedSize, Sort.by(orders));
    }

}
