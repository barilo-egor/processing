package net.rcetech.meta;

import java.util.List;
import java.util.Map;

/**
 * Запись в словаре констант. Все реализации этого интерфейса, помещенные в spring контекст, будут отображены фронту.
 */
public interface DictionaryField {
    /**
     *
     * @return ключ в словаре, иначе название JSON поля, обычно название класса перечисления: OrderStatus, ClientStatus
     */
    String getField();

    /**
     * @return контент, который будет помещен в качестве значения в словарь, иными словами JSON объект в поле {@link #getField()}
     */
    List<Map<String, Object>> getContent();
}
