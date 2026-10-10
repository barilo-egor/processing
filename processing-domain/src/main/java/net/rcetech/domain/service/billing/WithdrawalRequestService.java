package net.rcetech.domain.service.billing;

import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.WithdrawalRequestRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.util.CalculateUtil;
import net.rcetech.rates.RatePair;
import net.rcetech.rates.RateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class WithdrawalRequestService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;

    private final ClientService clientService;

    private final RateService rateService;

    public WithdrawalRequestService(WithdrawalRequestRepository withdrawalRequestRepository,
                                    ClientService clientService, RateService rateService) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.clientService = clientService;
        this.rateService = rateService;
    }


    @Transactional
    public WithdrawalRequest create(UUID clientId, Integer amount, String address) {
        Client client = clientService.findById(clientId)
                .orElseThrow(() -> new BaseException("Client with id " + clientId + " not found"));
        if (Objects.isNull(client.getCommissionPercent())) {
            // TODO Создание инцидента
            throw new ServiceUnavailableException("Не установлен процент комисси для клиента " + client + ".");
        }
        if (amount < client.getMinWithdrawalAmount()) {
            throw new BadRequestException("Запрошенная сумма на вывод меньше минимально допустимой.");
        }
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setId(UUID.randomUUID());
        withdrawalRequest.setClient(client);
        withdrawalRequest.setStatus(WithdrawalRequestStatus.NEW);
        withdrawalRequest.setCreatedAt(Instant.now());
        withdrawalRequest.setGrossSourceAmount(amount);
        withdrawalRequest.setCommissionPercent(client.getCommissionPercent());
        withdrawalRequest.setNetSourceAmount(CalculateUtil.subtractPercentCommission(amount, client.getCommissionPercent()));
        BigDecimal rate = rateService.getRate(RatePair.USDT_RUB);
        withdrawalRequest.setRate(rate);
        withdrawalRequest.setTargetAmount(CalculateUtil.convert(withdrawalRequest.getNetSourceAmount(), rate));
        withdrawalRequest.setAddress(address);
        return withdrawalRequestRepository.save(withdrawalRequest);
    }

    @Transactional(readOnly = true)
    public <T> Page<T> findAll(PredicateSpecification<WithdrawalRequest> filter, Pageable pageable, Class<T> projectionType) {
        return withdrawalRequestRepository.findBy(filter,
                query -> query.as(projectionType)
                        .page(PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                pageable.getSort()
                        )));
    }

    /**
     * Отмена заявки клиентом, в связи с чем проверяется соответствие автора заявки и отправителя запроса.
     * Отмена доступна только для заявок в статусе {@link WithdrawalRequestStatus#NEW}.
     * @param clientId отправитель запроса отмены заявки
     * @param id идентификатор заявки
     */
    @Transactional
    public void cancel(UUID clientId, UUID id) {
        Optional<WithdrawalRequest> maybeRequest = withdrawalRequestRepository.findById(id);
        if (maybeRequest.isEmpty() || !clientId.equals(maybeRequest.get().getClient().getId())) {
            throw new BadRequestException("Ордер не найден.");
        }
        WithdrawalRequest request = maybeRequest.get();
        if (!WithdrawalRequestStatus.NEW.equals(request.getStatus())) {
            throw new BadRequestException("Заявка должна быть в статусе \"Новая\".");
        }
        request.setStatus(WithdrawalRequestStatus.CANCELED);
    }

    /**
     * Обновление статуса заявки для сотрудников поддержки, так как отсутствует проверка автора заявки.
     * Отмена доступна только для заявок в статусе {@link WithdrawalRequestStatus#NEW}.
     * @param id идентификатор заявки
     * @param status новый статус
     */
    @Transactional
    public void updateStatus(UUID id, WithdrawalRequestStatus status) {
        Optional<WithdrawalRequest> maybeRequest = withdrawalRequestRepository.findById(id);
        if (maybeRequest.isEmpty()) {
            throw new BadRequestException("Ордер не найден.");
        }
        WithdrawalRequest request = maybeRequest.get();
        if (!WithdrawalRequestStatus.NEW.equals(request.getStatus())) {
            throw new BadRequestException("Заявка должна быть в статусе \"Новая\".");
        }
        request.setStatus(status);
    }

}
