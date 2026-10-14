package net.rcetech.support.controller;

import net.rcetech.domain.repository.incidents.IncidentSpecifications;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.IncidentFilter;
import net.rcetech.meta.incidents.dto.IncidentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private AuthenticationSuccessHandler successHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "TRADER"})
    @DisplayName("Метод должен вернуть 403, если роль пользователя не админ и не оператор.")
    void get_shouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(get("/api/private/incident")
                        .with(csrf())
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(incidentService);
    }

    @ParameterizedTest
    @CsvSource({
            "1,1790262068808,1790262268808,NEW,BALANCE_UPDATE_ERROR,1790262068000,1790262368000",
            "69643546,1790262058800,1790262090000,RESOLVED,MERCHANT_CALLBACK_STATUS_NOT_RESOLVED,1790262058000,1790262099000"
    })
    @DisplayName("Метод должен передать параметры в фильтр.")
    void get_shouldPassFilterParameters(long id,
                                        long createdAtFrom,
                                        long createdAtTo,
                                        IncidentStatus status,
                                        IncidentType type,
                                        long resolvedAtFrom,
                                        long resolvedAtTo) throws Exception {
        when(incidentService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(mock(IncidentResponse.class))));
        try (MockedStatic<IncidentSpecifications> mockedSpecifications
                     = Mockito.mockStatic(IncidentSpecifications.class)) {
            mockMvc.perform(get("/api/private/incident")
                    .with(csrf())
                    .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles("ADMIN"))
                    .queryParam("id", String.valueOf(id))
                    .queryParam("createdAtFrom", String.valueOf(createdAtFrom))
                    .queryParam("createdAtTo", String.valueOf(createdAtTo))
                    .queryParam("status", status.name())
                    .queryParam("type", type.name())
                    .queryParam("resolvedAtFrom", String.valueOf(resolvedAtFrom))
                    .queryParam("resolvedAtTo", String.valueOf(resolvedAtTo))
            ).andExpect(status().isOk());
            ArgumentCaptor<IncidentFilter> captor = ArgumentCaptor.forClass(IncidentFilter.class);
            mockedSpecifications.verify(() -> IncidentSpecifications.matches(captor.capture()));
            IncidentFilter filter = captor.getValue();
            assertAll(
                    () -> assertEquals(id, filter.id()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtFrom), filter.createdAtFrom()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtTo), filter.createdAtTo()),
                    () -> assertEquals(status, filter.status()),
                    () -> assertEquals(type, filter.type()),
                    () -> assertEquals(Instant.ofEpochMilli(resolvedAtFrom), filter.resolvedAtFrom()),
                    () -> assertEquals(Instant.ofEpochMilli(resolvedAtTo), filter.resolvedAtTo())
            );
        }
    }

    @Test
    @DisplayName("Метод должен использовать размер страницы 20 по умолчанию.")
    void get_shouldUseDefaultPageSize() throws Exception {
        when(incidentService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/private/incident")
                        .with(csrf())
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles("OPERATOR")))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(incidentService).findAll(any(), captor.capture(), eq(IncidentResponse.class));
        assertAll(
                () -> assertEquals(0, captor.getValue().getPageNumber()),
                () -> assertEquals(20, captor.getValue().getPageSize())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "1,1790262068808,NEW,,BALANCE_UPDATE_ERROR,,,",
            "69643546,1790262068808,RESOLVED,1790262068500,TRANSACTION_CREATION_ERROR,c2cbb845-7313-4106-a1a3-e9e4b328bbd9,1790262968000,Решено"
    })
    @DisplayName("Метод должен вернуть JSON представление инцидентов.")
    void get_shouldReturnJsonOfIncidentResponse(long id, long createdAt, IncidentStatus status, Long errorTimestamp,
                                                IncidentType type, UUID resolvedById, Long resolvedAt,
                                                String comment) throws Exception {
        IncidentResponse response = new IncidentResponse(
                id, Instant.ofEpochMilli(createdAt), status, errorTimestamp, type, Map.of("clientId", "123"),
                resolvedById, resolvedAt == null ? null : Instant.ofEpochMilli(resolvedAt), comment
        );
        when(incidentService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/private/incident")
                        .with(csrf())
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.content[0].createdAt").value(String.valueOf(createdAt)))
                .andExpect(jsonPath("$.content[0].status").value(status.name()))
                .andExpect(jsonPath("$.content[0].errorTimestamp").value(errorTimestamp == null ? null : String.valueOf(errorTimestamp)))
                .andExpect(jsonPath("$.content[0].type").value(type.name()))
                .andExpect(jsonPath("$.content[0].data.clientId").value("123"))
                .andExpect(jsonPath("$.content[0].resolvedById").value(resolvedById == null ? null : resolvedById.toString()))
                .andExpect(jsonPath("$.content[0].resolvedAt").value(resolvedAt == null ? null : String.valueOf(resolvedAt)))
                .andExpect(jsonPath("$.content[0].comment").value(comment))
                .andExpect(jsonPath("$.page").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "TRADER"})
    @DisplayName("Метод обновления должен вернуть 403, если роль пользователя не админ и не оператор.")
    void update_shouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(patch("/api/private/incident/1")
                        .with(csrf())
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(role))
                        .header("Content-type", "application/json")
                        .content("{\"comment\": \"Решено\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(incidentService);
    }

    @ParameterizedTest
    @CsvSource({
            "1,c2cbb845-7313-4106-a1a3-e9e4b328bbd9,ADMIN,Решено",
            "69643546,fe4a4710-9c2e-4f04-b274-06802fbf2012,OPERATOR,Баланс пересчитан вручную."
    })
    @DisplayName("Метод обновления должен передать id инцидента, id пользователя поддержки и комментарий в сервис.")
    void update_shouldPassParametersToService(long id, UUID supportUserId, String role, String comment) throws Exception {
        IncidentResponse response = mock(IncidentResponse.class);
        when(incidentService.resolve(any(), any(), any())).thenReturn(response);

        mockMvc.perform(patch("/api/private/incident/" + id)
                        .with(csrf())
                        .with(user(supportUserId.toString()).roles(role))
                        .header("Content-type", "application/json")
                        .content("{\"comment\": \"" + comment + "\"}"))
                .andExpect(status().isOk());

        verify(incidentService).resolve(id, supportUserId, comment);
    }

    @ParameterizedTest
    @CsvSource({
            "15,1790262068808,1790262068500,BALANCE_UPDATE_ERROR,clientId,123,c2cbb845-7313-4106-a1a3-e9e4b328bbd9,1790262968000,ADMIN,Решено",
            "69643546,1790262058000,1790262057999,MERCHANT_CALLBACK_HANDLE_ERROR,merchantOrderId,abc-1,fe4a4710-9c2e-4f04-b274-06802fbf2012,1790363000000,OPERATOR,Баланс пересчитан вручную."
    })
    @DisplayName("Метод обновления должен вернуть JSON представление обновленного инцидента.")
    void update_shouldReturnJsonOfIncidentResponse(long id, long createdAt, long errorTimestamp, IncidentType type,
                                                   String dataKey, String dataValue, UUID supportUserId,
                                                   long resolvedAt, String role, String comment) throws Exception {
        IncidentResponse response = new IncidentResponse(
                id, Instant.ofEpochMilli(createdAt), IncidentStatus.RESOLVED, errorTimestamp,
                type, Map.of(dataKey, dataValue), supportUserId, Instant.ofEpochMilli(resolvedAt), comment
        );
        when(incidentService.resolve(id, supportUserId, comment)).thenReturn(response);

        mockMvc.perform(patch("/api/private/incident/" + id)
                        .with(csrf())
                        .with(user(supportUserId.toString()).roles(role))
                        .header("Content-type", "application/json")
                        .content("{\"comment\": \"" + comment + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.createdAt").value(String.valueOf(createdAt)))
                .andExpect(jsonPath("$.status").value(IncidentStatus.RESOLVED.name()))
                .andExpect(jsonPath("$.errorTimestamp").value(String.valueOf(errorTimestamp)))
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.data." + dataKey).value(dataValue))
                .andExpect(jsonPath("$.resolvedById").value(supportUserId.toString()))
                .andExpect(jsonPath("$.resolvedAt").value(String.valueOf(resolvedAt)))
                .andExpect(jsonPath("$.comment").value(comment));
    }
}
