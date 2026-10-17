package net.rcetech.domain.service.incidents;

import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.domain.repository.incidents.IncidentRepository;
import net.rcetech.domain.repository.incidents.IncidentSpecifications;
import net.rcetech.domain.repository.support.SupportUserRepository;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.IncidentSaveErrorEvent;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.BalanceUpdateErrorData;
import net.rcetech.meta.incidents.dto.IncidentFilter;
import net.rcetech.meta.incidents.dto.IncidentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncidentService.class, SupportUserService.class})
@RecordApplicationEvents
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncidentServiceTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private SupportUserRepository supportUserRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @AfterEach
    void cleanUp() {
        incidentRepository.deleteAll();
        supportUserRepository.deleteAll();
    }

    void fillFields(Incident incident) {
        if (Objects.isNull(incident.getCreatedAt())) {
            incident.setCreatedAt(Instant.now());
        }
        if (Objects.isNull(incident.getStatus())) {
            incident.setStatus(IncidentStatus.NEW);
        }
        if (Objects.isNull(incident.getType())) {
            incident.setType(IncidentType.BALANCE_UPDATE_ERROR);
        }
        if (Objects.isNull(incident.getData())) {
            incident.setData(Map.of("transactionId", 1L));
        }
    }

    Incident saveDummyIncident(Incident incident) {
        fillFields(incident);
        return incidentRepository.save(incident);
    }

    Page<Incident> findAll(IncidentFilter filter) {
        return incidentService.findAll(
                IncidentSpecifications.matches(filter),
                PageRequest.of(0, 20),
                Incident.class
        );
    }

    @Test
    @DisplayName("Метод должен сохранить инцидент со статусом NEW, переданным типом, данными и временем ошибки.")
    void save_shouldSaveIncident() {
        UUID clientId = UUID.fromString("f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92");
        BalanceUpdateErrorData data = new BalanceUpdateErrorData(15L, clientId);
        long errorTimestamp = 1_760_000_000_000L;
        Instant before = Instant.now();

        incidentService.save(IncidentType.BALANCE_UPDATE_ERROR, data, errorTimestamp);

        List<Incident> actual = incidentRepository.findAll();
        assertEquals(1, actual.size());
        Incident incident = actual.getFirst();
        assertAll(
                () -> assertNotNull(incident.getId()),
                () -> assertEquals(IncidentType.BALANCE_UPDATE_ERROR, incident.getType()),
                () -> assertEquals(IncidentStatus.NEW, incident.getStatus()),
                () -> assertEquals(errorTimestamp, incident.getErrorTimestamp()),
                () -> assertNotNull(incident.getCreatedAt()),
                () -> assertFalse(incident.getCreatedAt().isBefore(before.minusSeconds(1))),
                () -> assertEquals(15, ((Number) incident.getData().get("transactionId")).longValue()),
                () -> assertEquals(clientId.toString(), incident.getData().get("clientId")),
                () -> assertNull(incident.getResolvedBy()),
                () -> assertNull(incident.getResolvedAt()),
                () -> assertNull(incident.getComment())
        );
        assertEquals(0, applicationEvents.stream(IncidentSaveErrorEvent.class).count());
    }

    @Test
    @DisplayName("Метод должен опубликовать IncidentSaveErrorEvent с типом и данными инцидента, если сохранить его не удалось.")
    void save_shouldPublishErrorEventIfSaveFailed() {
        String data = "not a map";

        incidentService.save(IncidentType.TRANSACTION_CREATION_ERROR, data, 1_760_000_000_000L);

        List<IncidentSaveErrorEvent> events = applicationEvents.stream(IncidentSaveErrorEvent.class).toList();
        assertEquals(1, events.size());
        IncidentSaveErrorEvent event = events.getFirst();
        assertAll(
                () -> assertEquals(IncidentType.TRANSACTION_CREATION_ERROR, event.type()),
                () -> assertSame(data, event.data())
        );
        assertEquals(0, incidentRepository.count());
    }

    @Test
    @DisplayName("Метод должен опубликовать IncidentSaveErrorEvent и не бросать исключение, если БД отклонила инцидент.")
    void save_shouldPublishErrorEventIfDatabaseRejectedIncident() {
        assertDoesNotThrow(() -> incidentService.save(IncidentType.BALANCE_UPDATE_ERROR, null, 1_760_000_000_000L));

        List<IncidentSaveErrorEvent> events = applicationEvents.stream(IncidentSaveErrorEvent.class).toList();
        assertEquals(1, events.size());
        IncidentSaveErrorEvent event = events.getFirst();
        assertAll(
                () -> assertEquals(IncidentType.BALANCE_UPDATE_ERROR, event.type()),
                () -> assertNull(event.data())
        );
        assertEquals(0, incidentRepository.count());
    }

    @Test
    @DisplayName("Метод должен вернуть все инциденты, если фильтр равен null.")
    void findAll_shouldReturnAllIncidentsIfNullFilter() {
        for (int i = 0; i < 3; i++) {
            saveDummyIncident(new Incident());
        }

        Page<Incident> incidents = findAll(null);

        assertEquals(3, incidents.getTotalElements());
    }

    @Test
    @DisplayName("Метод должен вернуть все инциденты, если все поля фильтра равны null.")
    void findAll_shouldReturnAllIncidentsIfEmptyFilter() {
        for (int i = 0; i < 3; i++) {
            saveDummyIncident(new Incident());
        }

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, null, null, null, null
        ));

        assertEquals(3, incidents.getTotalElements());
    }

    @Test
    @DisplayName("Метод должен найти инцидент по id.")
    void findAll_shouldFindIncidentById() {
        Incident targetIncident = saveDummyIncident(new Incident());
        saveDummyIncident(new Incident());

        Page<Incident> incidents = findAll(new IncidentFilter(
                targetIncident.getId(), null, null, null, null, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068000L, 1790262062000L
    })
    @DisplayName("Метод должен найти инцидент по createdAt ОТ.")
    void findAll_shouldFindIncidentByCreatedAtFrom(long millis) {
        Incident targetIncident = new Incident();
        targetIncident.setCreatedAt(Instant.ofEpochMilli(millis + 50000L));
        saveDummyIncident(targetIncident);
        Incident incident = new Incident();
        incident.setCreatedAt(Instant.ofEpochMilli(millis - 2000L));
        saveDummyIncident(incident);

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, Instant.ofEpochMilli(millis), null, null, null, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068L, 1790262062L
    })
    @DisplayName("Метод должен включать границу createdAt ОТ.")
    void findAll_shouldIncludeCreatedAtFromBoundary(long seconds) {
        Instant from = Instant.ofEpochSecond(seconds);
        Incident targetIncident = new Incident();
        targetIncident.setCreatedAt(from);
        saveDummyIncident(targetIncident);
        Incident incident = new Incident();
        incident.setCreatedAt(from.minusSeconds(1));
        saveDummyIncident(incident);

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, from, null, null, null, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068000L, 1790262062000L
    })
    @DisplayName("Метод должен найти инцидент по createdAt ДО.")
    void findAll_shouldFindIncidentByCreatedAtTo(long millis) {
        Incident targetIncident = new Incident();
        targetIncident.setCreatedAt(Instant.ofEpochMilli(millis - 50000L));
        saveDummyIncident(targetIncident);
        Incident incident = new Incident();
        incident.setCreatedAt(Instant.ofEpochMilli(millis + 2000L));
        saveDummyIncident(incident);

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, Instant.ofEpochMilli(millis), null, null, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068L, 1790262062L
    })
    @DisplayName("Метод должен исключать границу createdAt ДО.")
    void findAll_shouldExcludeCreatedAtToBoundary(long seconds) {
        Instant to = Instant.ofEpochSecond(seconds);
        Incident incident = new Incident();
        incident.setCreatedAt(to);
        saveDummyIncident(incident);
        Incident targetIncident = new Incident();
        targetIncident.setCreatedAt(to.minusSeconds(1));
        saveDummyIncident(targetIncident);

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, to, null, null, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    @DisplayName("Метод должен найти инцидент по статусу.")
    void findAll_shouldFindIncidentByStatus(IncidentStatus status) {
        Incident targetIncident = new Incident();
        targetIncident.setStatus(status);
        saveDummyIncident(targetIncident);
        Incident incident = new Incident();
        incident.setStatus(status == IncidentStatus.NEW ? IncidentStatus.RESOLVED : IncidentStatus.NEW);
        saveDummyIncident(incident);

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, status, null, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @EnumSource(IncidentType.class)
    @DisplayName("Метод должен найти инцидент по типу.")
    void findAll_shouldFindIncidentByType(IncidentType type) {
        Incident targetIncident = new Incident();
        targetIncident.setType(type);
        saveDummyIncident(targetIncident);
        for (IncidentType otherType : IncidentType.values()) {
            if (otherType != type) {
                Incident incident = new Incident();
                incident.setType(otherType);
                saveDummyIncident(incident);
            }
        }

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, null, type, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068000L, 1790262062000L
    })
    @DisplayName("Метод должен найти инцидент по resolvedAt ОТ.")
    void findAll_shouldFindIncidentByResolvedAtFrom(long millis) {
        Incident targetIncident = saveResolvedIncident(Instant.ofEpochMilli(millis + 50000L));
        saveResolvedIncident(Instant.ofEpochMilli(millis - 2000L));
        saveDummyIncident(new Incident());

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, null, null, Instant.ofEpochMilli(millis), null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068000L, 1790262062000L
    })
    @DisplayName("Метод должен найти инцидент по resolvedAt ДО.")
    void findAll_shouldFindIncidentByResolvedAtTo(long millis) {
        Incident targetIncident = saveResolvedIncident(Instant.ofEpochMilli(millis - 50000L));
        saveResolvedIncident(Instant.ofEpochMilli(millis + 2000L));
        saveDummyIncident(new Incident());

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, null, null, null, Instant.ofEpochMilli(millis)
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068L, 1790262062L
    })
    @DisplayName("Метод должен включать границу resolvedAt ОТ.")
    void findAll_shouldIncludeResolvedAtFromBoundary(long seconds) {
        Instant from = Instant.ofEpochSecond(seconds);
        Incident targetIncident = saveResolvedIncident(from);
        saveResolvedIncident(from.minusSeconds(1));

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, null, null, from, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068L, 1790262062L
    })
    @DisplayName("Метод должен исключать границу resolvedAt ДО.")
    void findAll_shouldExcludeResolvedAtToBoundary(long seconds) {
        Instant to = Instant.ofEpochSecond(seconds);
        saveResolvedIncident(to);
        Incident targetIncident = saveResolvedIncident(to.minusSeconds(1));

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, null, null, null, to
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    Incident saveResolvedIncident(Instant resolvedAt) {
        Incident incident = new Incident();
        incident.setCreatedAt(resolvedAt);
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedBy(saveDummySupportUser());
        incident.setResolvedAt(resolvedAt);
        return saveDummyIncident(incident);
    }

    @Test
    @DisplayName("Метод должен объединять условия фильтра через AND.")
    void findAll_shouldCombineFilterConditions() {
        Incident targetIncident = new Incident();
        targetIncident.setStatus(IncidentStatus.RESOLVED);
        targetIncident.setType(IncidentType.TRANSACTION_CREATION_ERROR);
        saveDummyIncident(targetIncident);
        Incident sameStatusIncident = new Incident();
        sameStatusIncident.setStatus(IncidentStatus.RESOLVED);
        sameStatusIncident.setType(IncidentType.BALANCE_UPDATE_ERROR);
        saveDummyIncident(sameStatusIncident);
        Incident sameTypeIncident = new Incident();
        sameTypeIncident.setStatus(IncidentStatus.NEW);
        sameTypeIncident.setType(IncidentType.TRANSACTION_CREATION_ERROR);
        saveDummyIncident(sameTypeIncident);

        Page<Incident> incidents = findAll(new IncidentFilter(
                null, null, null, IncidentStatus.RESOLVED, IncidentType.TRANSACTION_CREATION_ERROR, null, null
        ));

        assertEquals(1, incidents.getTotalElements());
        assertEquals(targetIncident.getId(), incidents.getContent().getFirst().getId());
    }

    SupportUser saveDummySupportUser() {
        SupportUser supportUser = new SupportUser();
        supportUser.setId(UUID.randomUUID());
        supportUser.setUsername("support" + supportUser.getId());
        return supportUserRepository.save(supportUser);
    }

    @ParameterizedTest
    @CsvSource({
            "BALANCE_UPDATE_ERROR,Баланс пересчитан вручную.",
            "TRANSACTION_CREATION_ERROR,Создана корректировочная транзакция."
    })
    @DisplayName("Метод должен проставить инциденту статус RESOLVED, пользователя поддержки, время решения и комментарий.")
    void resolve_shouldResolveIncident(IncidentType type, String comment) {
        SupportUser supportUser = saveDummySupportUser();
        Incident incident = new Incident();
        incident.setType(type);
        saveDummyIncident(incident);
        Instant before = Instant.now();

        incidentService.resolve(incident.getId(), supportUser.getId(), comment);

        Incident actual = incidentRepository.findById(incident.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(IncidentStatus.RESOLVED, actual.getStatus()),
                () -> assertEquals(type, actual.getType()),
                () -> assertEquals(supportUser.getId(), actual.getResolvedBy().getId()),
                () -> assertNotNull(actual.getResolvedAt()),
                () -> assertFalse(actual.getResolvedAt().isBefore(before.minusSeconds(1))),
                () -> assertEquals(comment, actual.getComment())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "BALANCE_UPDATE_ERROR,1790262068500,Баланс пересчитан вручную.",
            "MERCHANT_CALLBACK_HANDLE_ERROR,1790262057999,Ордер проведен вручную."
    })
    @DisplayName("Метод должен вернуть обновленный инцидент.")
    void resolve_shouldReturnResolvedIncident(IncidentType type, long errorTimestamp, String comment) {
        SupportUser supportUser = saveDummySupportUser();
        Incident incident = new Incident();
        incident.setType(type);
        incident.setErrorTimestamp(errorTimestamp);
        saveDummyIncident(incident);
        Instant before = Instant.now();

        IncidentResponse response = incidentService.resolve(incident.getId(), supportUser.getId(), comment);

        Incident actual = incidentRepository.findById(incident.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(incident.getId(), response.id()),
                () -> assertEquals(IncidentStatus.RESOLVED, response.status()),
                () -> assertEquals(errorTimestamp, response.errorTimestamp()),
                () -> assertEquals(type, response.type()),
                () -> assertEquals(supportUser.getId(), response.resolvedById()),
                () -> assertNotNull(response.resolvedAt()),
                () -> assertFalse(response.resolvedAt().isBefore(before.minusSeconds(1))),
                () -> assertEquals(actual.getResolvedAt(), response.resolvedAt()),
                () -> assertEquals(comment, response.comment())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Метод должен сохранить null в комментарий, если передан пустой комментарий.")
    void resolve_shouldSaveNullCommentIfBlank(String comment) {
        SupportUser supportUser = saveDummySupportUser();
        Incident incident = saveDummyIncident(new Incident());

        incidentService.resolve(incident.getId(), supportUser.getId(), comment);

        Incident actual = incidentRepository.findById(incident.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(IncidentStatus.RESOLVED, actual.getStatus()),
                () -> assertNull(actual.getComment())
        );
    }

    @Test
    @DisplayName("Метод должен бросить BaseException и не изменять инцидент, если пользователь поддержки не найден.")
    void resolve_shouldThrowIfSupportUserNotFound() {
        Incident incident = saveDummyIncident(new Incident());
        UUID supportUserId = UUID.randomUUID();
        Long incidentId = incident.getId();

        assertThrows(BaseException.class,
                () -> incidentService.resolve(incidentId, supportUserId, "comment"));

        Incident actual = incidentRepository.findById(incident.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(IncidentStatus.NEW, actual.getStatus()),
                () -> assertNull(actual.getResolvedAt()),
                () -> assertNull(actual.getComment())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "1790262068,Баланс пересчитан вручную.",
            "1790362099,Создана корректировочная транзакция."
    })
    @DisplayName("Метод должен бросить BaseException и не изменять инцидент, если инцидент не в статусе NEW.")
    void resolve_shouldThrowIfIncidentNotNew(long resolvedAtSeconds, String comment) {
        SupportUser resolvedBy = saveDummySupportUser();
        SupportUser supportUser = saveDummySupportUser();
        Instant resolvedAt = Instant.ofEpochSecond(resolvedAtSeconds);
        Incident incident = new Incident();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedBy(resolvedBy);
        incident.setResolvedAt(resolvedAt);
        incident.setComment(comment);
        saveDummyIncident(incident);
        Long incidentId = incident.getId();
        UUID supportUserId = supportUser.getId();

        assertThrows(BaseException.class,
                () -> incidentService.resolve(incidentId, supportUserId, "Повторное решение."));

        Incident actual = incidentRepository.findById(incident.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(IncidentStatus.RESOLVED, actual.getStatus()),
                () -> assertEquals(resolvedBy.getId(), actual.getResolvedBy().getId()),
                () -> assertEquals(resolvedAt, actual.getResolvedAt()),
                () -> assertEquals(comment, actual.getComment())
        );
    }

    @Test
    @DisplayName("Метод должен бросить BadRequestException, если инцидент не найден.")
    void resolve_shouldThrowIfIncidentNotFound() {
        SupportUser supportUser = saveDummySupportUser();
        UUID supportUserId = supportUser.getId();

        assertThrows(BadRequestException.class,
                () -> incidentService.resolve(-1L, supportUserId, "comment"));
    }

    @ParameterizedTest
    @CsvSource({
            "1790262068500,",
            "1790262057999,1790262068000"
    })
    @DisplayName("Метод должен вернуть в IncidentResponse решенный и нерешенный инциденты с корректными полями решения.")
    void findAll_shouldReturnResolvedAndNotResolvedIncidents(Long resolvedErrorTimestamp, Long newErrorTimestamp) {
        SupportUser supportUser = saveDummySupportUser();
        Incident resolvedIncident = new Incident();
        resolvedIncident.setErrorTimestamp(resolvedErrorTimestamp);
        saveDummyIncident(resolvedIncident);
        Incident newIncident = new Incident();
        newIncident.setErrorTimestamp(newErrorTimestamp);
        saveDummyIncident(newIncident);
        incidentService.resolve(resolvedIncident.getId(), supportUser.getId(), "comment");
        Instant resolvedAt = incidentRepository.findById(resolvedIncident.getId()).orElseThrow().getResolvedAt();

        Page<IncidentResponse> incidents = incidentService.findAll(
                IncidentSpecifications.matches(null),
                PageRequest.of(0, 20),
                IncidentResponse.class
        );

        assertEquals(2, incidents.getTotalElements());
        Map<Long, IncidentResponse> byId = incidents.getContent().stream()
                .collect(Collectors.toMap(IncidentResponse::id, Function.identity()));
        assertAll(
                () -> assertEquals(supportUser.getId(), byId.get(resolvedIncident.getId()).resolvedById()),
                () -> assertNotNull(resolvedAt),
                () -> assertEquals(resolvedAt, byId.get(resolvedIncident.getId()).resolvedAt()),
                () -> assertEquals(resolvedErrorTimestamp, byId.get(resolvedIncident.getId()).errorTimestamp()),
                () -> assertEquals("comment", byId.get(resolvedIncident.getId()).comment()),
                () -> assertTrue(byId.containsKey(newIncident.getId())),
                () -> assertNull(byId.get(newIncident.getId()).resolvedById()),
                () -> assertNull(byId.get(newIncident.getId()).resolvedAt()),
                () -> assertEquals(newErrorTimestamp, byId.get(newIncident.getId()).errorTimestamp())
        );
    }
}
