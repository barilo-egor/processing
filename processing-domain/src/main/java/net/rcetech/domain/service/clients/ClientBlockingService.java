package net.rcetech.domain.service.clients;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.meta.clients.ClientBlockChangedEvent;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Блокировка и разблокировка клиента поддержкой.
 */
@Slf4j
@Service
public class ClientBlockingService {

    private static final String CLIENT_NOT_FOUND = "Клиент не найден.";

    private final ClientRepository clientRepository;

    private final ClientService clientService;

    private final ApplicationEventPublisher eventPublisher;

    private final TransactionTemplate transactionTemplate;

    public ClientBlockingService(ClientRepository clientRepository, ClientService clientService,
                                 ApplicationEventPublisher eventPublisher,
                                 PlatformTransactionManager transactionManager) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Блокирует клиента. Блокировка сохраняется до обращения к внешним системам, поэтому ошибка при закрытии
     * доступа в них не снимает блокировку в приложении. Момент блокировки у уже заблокированного клиента
     * не меняется, событие публикуется повторно, чтобы ещё раз закрыть доступ во внешних системах.
     *
     * @param id идентификатор клиента
     * @return клиент после блокировки
     */
    public SupportClientProjection block(UUID id) {
        Client client = Objects.requireNonNull(transactionTemplate.execute(status -> change(id, true)));
        eventPublisher.publishEvent(new ClientBlockChangedEvent(id, client.getUsername(), true));
        return findById(id);
    }

    /**
     * Снимает блокировку с клиента. Если доступ во внешних системах вернуть не удалось, клиент остаётся
     * заблокированным.
     *
     * @param id идентификатор клиента
     * @return клиент после разблокировки
     */
    @Transactional
    public SupportClientProjection unblock(UUID id) {
        Client client = change(id, false);
        eventPublisher.publishEvent(new ClientBlockChangedEvent(id, client.getUsername(), false));
        return findById(id);
    }

    private Client change(UUID id, boolean blocked) {
        Client client = clientRepository.findForUpdateById(id)
                .orElseThrow(() -> new BadRequestException(CLIENT_NOT_FOUND));
        if (!blocked) {
            client.setBlockedAt(null);
        } else if (Objects.isNull(client.getBlockedAt())) {
            client.setBlockedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
        clientRepository.saveAndFlush(client);
        log.info("Клиент {} ({}) {} в приложении.", id, client.getUsername(),
                blocked ? "заблокирован" : "разблокирован");
        return client;
    }

    private SupportClientProjection findById(UUID id) {
        return clientService.findById(id, SupportClientProjection.class)
                .orElseThrow(() -> new BadRequestException(CLIENT_NOT_FOUND));
    }
}
