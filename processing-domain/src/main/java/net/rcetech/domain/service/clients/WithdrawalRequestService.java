package net.rcetech.domain.service.clients;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.mapper.client.WithdrawalMapper;
import net.rcetech.domain.model.clients.WithdrawalRequest;
import net.rcetech.domain.repository.clients.WithdrawalRequestRepository;
import net.rcetech.meta.clients.dto.WithdrawalRequestDTO;
import net.rcetech.meta.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class WithdrawalRequestService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;

    private final WithdrawalMapper mapper;

    private final ApplicationEventPublisher eventPublisher;

    public WithdrawalRequestService(WithdrawalRequestRepository withdrawalRequestRepository,
            WithdrawalMapper mapper, @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Сохраняет заявку на вывод средств в базе данных и публикует событие.
     * После успешного сохранения сущность конвертируется обратно в DTO
     * и публикуется в {@link ApplicationEventPublisher} (если он инициализирован).
     *
     * @param withdrawalRequest DTO с данными заявки на вывод средств
     */
    public void saveWithdrawalRequest(WithdrawalRequestDTO withdrawalRequest) {
        WithdrawalRequest saved = withdrawalRequestRepository.save(mapper.requestDTOToEntity(withdrawalRequest));
        WithdrawalRequestDTO dto = mapper.entityToDTO(saved);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(dto);
        }
    }

    /**
     * Обновляет реквизиты кошелька и комментарий в существующей заявке на вывод средств.
     * Метод выполняет поиск заявки в базе данных по её идентификатору и обновляет только
     * разрешенные для изменения поля (кошелек и комментарий).
     *
     * @param id                   уникальный идентификатор изменяемой заявки
     * @param withdrawalRequestDTO новые данные для обновления
     * @throws NotFoundException        если заявка с указанным {@code id} не найдена в системе
     */
    public void updateWithdrawalRequest(@NonNull Long id, WithdrawalRequestDTO withdrawalRequestDTO) {
        WithdrawalRequest withdrawalRequest = withdrawalRequestRepository.findById(id).orElseThrow(() ->
                new NotFoundException(String.valueOf(id))
        );
        withdrawalRequest.setWallet(withdrawalRequestDTO.getWallet());
        withdrawalRequest.setComment(withdrawalRequestDTO.getComment());
    }

}
