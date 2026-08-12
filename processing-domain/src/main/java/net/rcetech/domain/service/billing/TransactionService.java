package net.rcetech.domain.service.billing;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.mapper.billing.TransactionMapper;
import net.rcetech.meta.billing.dto.TransactionDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.repository.billing.TransactionRepository;
import net.rcetech.domain.util.PageableUtils;

import java.util.List;

@Service
@Slf4j
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;

    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    /**
     * Создает новую transaction, если она не была создана ранее.
     * <p>
     * Метод идемпотентен: если transaction с указанным ID уже существует в базе данных,
     * операция логируется как предупреждение и завершается без повторного сохранения.
     *
     * @param transactionDTO данные создаваемой transaction (обязательно должен содержать валидный ID)
     */
    public void create(TransactionDTO transactionDTO) {
        log.debug("Вызов create для transaction: {}", transactionDTO);
        if (transactionRepository.existsById(transactionDTO.getId())) {
            // Изменено: добавлен контекст "при создании"
            log.warn("Дубликат при создании! Transaction с id {} уже существует.", transactionDTO.getId());
            return;
        }
        Transaction transaction = transactionMapper.toEntity(transactionDTO);
        transactionRepository.save(transaction);
        log.debug("Успешно создана transaction: {}", transaction.getId());
    }

    /**
     * Сохраняет transaction в базе данных, если она не была создана ранее.
     * <p>
     * Перед записью проверяет существование записи по ID. Если transaction уже есть,
     * operation логируется как предупреждение и завершается без перезаписи данных.
     *
     * @param transactionDTO данные сохраняемой transaction
     */
    public void save(TransactionDTO transactionDTO) {
        log.debug("Вызов save для transaction: {}", transactionDTO);
        if (transactionRepository.existsById(transactionDTO.getId())) {
            log.warn("Пропуск сохранения! Transaction с id {} уже существует.", transactionDTO.getId());
            return;
        }
        Transaction transaction = transactionRepository.save(transactionMapper.toEntity(transactionDTO));
        log.debug("Успешно сохранена transaction: {}", transaction.getId());
    }

    /**
     * Возвращает страницу transaction, соответствующих заданным критериям фильтрации и сортировки.
     * <p>
     *
     * @param spec    спецификация JPA с критериями фильтрации полей
     * @param page    номер запрашиваемой страницы (начиная с 0)
     * @param size    количество записей на одной странице
     * @param sorters список строк для настройки направления сортировки (например, "id,desc")
     * @return страница {@link Page} с результатами поиска, смаппированными в {@link TransactionDTO}
     */
    @Transactional(readOnly = true)
    public Page<TransactionDTO> findTransactions(Specification<Transaction> spec, int page, int size,
            List<String> sorters) {
        Pageable pageable = PageableUtils.createPageable(page, size, sorters);
        return transactionRepository.findAll(spec, pageable).map(transactionMapper::entityToDTO);
    }

}
