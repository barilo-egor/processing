package tgb.cryptoexchange.clientsapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tgb.cryptoexchange.clientsapi.dto.WithdrawalRequestDTO;
import tgb.cryptoexchange.clientsapi.entity.WithdrawalRequest;
import tgb.cryptoexchange.clientsapi.exceptions.FieldNotBeEmptyException;
import tgb.cryptoexchange.clientsapi.exceptions.NotFoundException;
import tgb.cryptoexchange.clientsapi.mapper.WithdrawalMapper;
import tgb.cryptoexchange.clientsapi.repository.WithdrawalRequestRepository;

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
     * @throws FieldNotBeEmptyException если переданный идентификатор {@code id} равен {@code null}
     * @throws NotFoundException        если заявка с указанным {@code id} не найдена в системе
     */
    public void updateWithdrawalRequest(Long id, WithdrawalRequestDTO withdrawalRequestDTO) {
        if (id == null) {
            throw new FieldNotBeEmptyException("id");
        }
        WithdrawalRequest withdrawalRequest = withdrawalRequestRepository.findById(id).orElseThrow(() ->
                new NotFoundException(String.valueOf(id))
        );
        withdrawalRequest.setWallet(withdrawalRequestDTO.getWallet());
        withdrawalRequest.setComment(withdrawalRequestDTO.getComment());
    }

}
