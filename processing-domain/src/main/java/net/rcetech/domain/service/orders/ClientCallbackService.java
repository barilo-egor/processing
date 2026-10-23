package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.orders.ClientCallback;
import net.rcetech.domain.repository.orders.ClientCallbackRepository;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.ClientCallbackDeliveryUnknownIncident;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.orders.ClientCallbackStatus;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ClientCallbackService {

    private final ClientCallbackRepository clientCallbackRepository;

    private final IncidentService incidentService;

    public ClientCallbackService(ClientCallbackRepository clientCallbackRepository, IncidentService incidentService) {
        this.clientCallbackRepository = clientCallbackRepository;
        this.incidentService = incidentService;
    }

    @Transactional
    public ClientCallback create(UUID orderId, OrderStatus orderStatus, String url, String payload) {
        Instant now = Instant.now();
        ClientCallback callback = new ClientCallback();
        callback.setCreatedAt(now);
        callback.setOrderId(orderId);
        callback.setOrderStatus(orderStatus);
        callback.setUrl(url);
        callback.setPayload(payload);
        callback.setStatus(ClientCallbackStatus.PENDING);
        callback.setNextAttemptAt(now);
        return clientCallbackRepository.save(callback);
    }

    @Transactional(readOnly = true)
    public List<ClientCallback> findReadyToSend(int limit) {
        return clientCallbackRepository.findReadyToSend(Instant.now(), Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public List<ClientCallback> findStuckInSending(Instant lastAttemptBefore) {
        return clientCallbackRepository.findAllByStatusAndLastAttemptAtBefore(ClientCallbackStatus.SENDING,
                lastAttemptBefore);
    }

    @Transactional
    public ClientCallback markSending(Long id) {
        ClientCallback callback = getById(id);
        callback.setAttempts(callback.getAttempts() + 1);
        callback.setStatus(ClientCallbackStatus.SENDING);
        callback.setLastAttemptAt(Instant.now());
        return callback;
    }

    @Transactional
    public void markSent(Long id) {
        ClientCallback callback = getById(id);
        callback.setStatus(ClientCallbackStatus.SENT);
        callback.setSentAt(Instant.now());
    }

    @Transactional
    public void scheduleRetry(Long id, Instant nextAttemptAt) {
        ClientCallback callback = getById(id);
        callback.setStatus(ClientCallbackStatus.PENDING);
        callback.setNextAttemptAt(nextAttemptAt);
    }

    @Transactional
    public void markFailed(Long id) {
        ClientCallback callback = getById(id);
        callback.setStatus(ClientCallbackStatus.FAILED);
    }

    @Transactional
    public void markDeliveryUnknown(Long id, long errorTimestamp) {
        ClientCallback callback = getById(id);
        callback.setStatus(ClientCallbackStatus.FAILED);
        incidentService.saveInCurrentTransaction(IncidentType.CLIENT_CALLBACK_DELIVERY_UNKNOWN,
                new ClientCallbackDeliveryUnknownIncident(callback.getOrderId(), callback.getOrderStatus(),
                        callback.getUrl()), errorTimestamp);
    }

    @Transactional
    public int deleteFinishedBefore(Instant createdBefore) {
        return clientCallbackRepository.deleteAllByStatusInAndCreatedAtBefore(
                List.of(ClientCallbackStatus.SENT, ClientCallbackStatus.FAILED), createdBefore);
    }

    private ClientCallback getById(Long id) {
        return clientCallbackRepository.findById(id)
                .orElseThrow(() -> new BaseException("КБ клиенту не найден по идентификатору " + id + "."));
    }
}
