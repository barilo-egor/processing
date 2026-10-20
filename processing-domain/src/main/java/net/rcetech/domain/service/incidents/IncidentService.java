package net.rcetech.domain.service.incidents;

import jakarta.transaction.Transactional;
import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.repository.incidents.IncidentRepository;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.IncidentSaveErrorEvent;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.IncidentResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final TransactionTemplate transactionTemplate;

    private final SupportUserService supportUserService;

    public IncidentService(IncidentRepository incidentRepository, ObjectMapper objectMapper,
                           ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager,
                           SupportUserService supportUserService) {
        this.incidentRepository = incidentRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.supportUserService = supportUserService;
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void saveIfNoUnresolved(IncidentType type, Object data, Long errorTimestamp) {
        if (incidentRepository.existsByTypeAndStatus(type, IncidentStatus.NEW)) {
            return;
        }
        save(type, data, errorTimestamp);
    }

    public void save(IncidentType type, Object data, Long errorTimestamp) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Incident incident = new Incident();
                incident.setCreatedAt(Instant.now());
                incident.setErrorTimestamp(errorTimestamp);
                incident.setStatus(IncidentStatus.NEW);
                incident.setType(type);
                incident.setData(objectMapper.convertValue(data, new TypeReference<>() {}));
                incidentRepository.save(incident);
            });
        } catch (Exception e) {
            eventPublisher.publishEvent(new IncidentSaveErrorEvent(type, data, e));
        }
    }

    public <T> Page<T> findAll(PredicateSpecification<Incident> filter, Pageable pageable, Class<T> projectionType) {
        return incidentRepository.findBy(filter,
                query -> query.as(projectionType)
                        .page(PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                pageable.getSort()
                        )));
    }

    public <T> Optional<T> findById(Long id, Class<T> projectionType) {
        Incident order = new Incident();
        order.setId(id);
        return incidentRepository.findBy(Example.of(order), query -> query.as(projectionType).one());
    }

    @Transactional
    public IncidentResponse resolve(Long id, UUID supportUserId, String comment) {
        SupportUser supportUser = supportUserService.findById(supportUserId, SupportUser.class)
                .orElseThrow(() -> new BaseException("Пользователь поддержки " + supportUserId + " не найден."));
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Инцидент с идентификатором " + id + " не найден."));
        if (!IncidentStatus.NEW.equals(incident.getStatus())) {
            throw new BaseException("Инцидент может быть обработан только в статусе NEW");
        }
        incident.setResolvedBy(supportUser);
        incident.setResolvedAt(Instant.now());
        incident.setComment(Objects.isNull(comment) || comment.isBlank() ? null : comment);
        incident.setStatus(IncidentStatus.RESOLVED);
        return findById(id, IncidentResponse.class)
                .orElseThrow(() -> new BaseException("Не найден обновленный инцидент"));
    }
}
