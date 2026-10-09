package net.rcetech.clients.controller;

import net.rcetech.clients.config.ClientsSecurityConfig;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.orders.RequestMethod;
import org.hamcrest.Matchers;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportClientController.class)
@Import({ClientsSecurityConfig.class, MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class SupportClientControllerTest {

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ClientService clientService;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Метод должен вернуть пустой массив.")
    void getClients_shouldReturnEmptyArray() throws Exception {
        when(clientService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(new ArrayList<>()));
        mockMvc.perform(get("/api/private/client")
                        .queryParam("username", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.page.size").value(0))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Метод должен вернуть JSON список клиентов.")
    void getClients_shouldReturnClients(int clientsSize) throws Exception {
        List<SupportClientProjection> clients = new ArrayList<>();
        for (int i = 0; i < clientsSize; i++) {
            SupportClientProjection client = getClient(i);
            clients.add(client);
        }
        Page<SupportClientProjection> page = new PageImpl<>(
                clients, PageRequest.of(0, 100), clients.size()
        );
        when(clientService.findAll(any(), any(), eq(SupportClientProjection.class))).thenReturn(page);
        ResultActions resultActions = mockMvc.perform(get("/api/private/client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        int i = 0;
        for (SupportClientProjection client : clients) {
            resultActions.andExpect(jsonPath("$.content[" + i + "].id").value(client.getId().toString()));
            resultActions.andExpect(jsonPath("$.content[" + i + "].username").value(client.getUsername()));
            resultActions.andExpect(jsonPath("$.content[" + i + "].registeredAt").value(client.getRegisteredAt().toEpochMilli()));
            resultActions.andExpect(jsonPath("$.content[" + i + "].callbackUrl").value("https://example.com/callback"));
            resultActions.andExpect(jsonPath("$.content[" + i + "].orderTimeoutSeconds").value(900));
            resultActions.andExpect(jsonPath("$.content[" + i + "].commissionPercent").value("20.5"));
            resultActions.andExpect(jsonPath("$.content[" + i + "].balance").isNumber());
            resultActions.andExpect(jsonPath("$.content[" + i + "].balance").value("154789"));
            resultActions.andExpect(jsonPath("$.content[" + i + "].methods").isArray());
            resultActions.andExpect(jsonPath("$.content[" + i + "].methods", Matchers.hasItems("CARD", "SBP")));
            resultActions.andExpect(jsonPath("$.content[" + i + "].comment").value("comment"));
            resultActions.andExpect(jsonPath("$.content[" + i + "].minWithdrawalAmount").value(50000));
            i++;
        }
    }

    private static @NonNull SupportClientProjection getClient(int i) {
        UUID id = UUID.randomUUID();
        String username = "test" + i;
        Instant now = Instant.now();
        return new SupportClientProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Instant getRegisteredAt() {
                return now;
            }

            @Override
            public String getCallbackUrl() {
                return "https://example.com/callback";
            }

            @Override
            public Integer getOrderTimeoutSeconds() {
                return 900;
            }

            @Override
            public BigDecimal getCommissionPercent() {
                return new BigDecimal("20.5");
            }

            @Override
            public Integer getBalance() {
                return 154789;
            }

            @Override
            public Set<RequestMethod> getMethods() {
                return Set.of(RequestMethod.CARD, RequestMethod.SBP);
            }

            @Override
            public String getComment() {
                return "comment";
            }

            @Override
            public Integer getMinWithdrawalAmount() {
                return 50000;
            }
        };
    }

    @CsvSource("""
            b8ee52fd-e7ed-4004-8c1f-955a76719685,admin,ACTIVE,1787659000000,1787659000123,10,3
            c6e5d372-9436-4cdb-a6c4-dc33c0fc8d7a,user_poser,BLOCKED,1787659000000,1787659000987,20,10
            """)
    @ParameterizedTest
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Метод должен передать параметры фильтрации в метод сервиса.")
    void getClients_shouldPassParametersToMethod(String id, String username, String status, long from, long to,
                                                 int size, int page) throws Exception {
        when(clientService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(new ArrayList<>()));
        ArgumentCaptor<ClientFilter> filterCaptor = ArgumentCaptor.forClass(ClientFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        mockMvc.perform(get("/api/private/client")
                .queryParam("id", id)
                .queryParam("username", username)
                .queryParam("status", status)
                .queryParam("from", String.valueOf(from))
                .queryParam("to", String.valueOf(to))
                .queryParam("page", String.valueOf(page))
                .queryParam("size", String.valueOf(size))
        ).andExpect(status().isOk());
        verify(clientService).findAll(filterCaptor.capture(), pageableCaptor.capture(), eq(SupportClientProjection.class));
        ClientFilter filter = filterCaptor.getValue();
        Pageable pageable = pageableCaptor.getValue();
        assertAll(
                () -> assertEquals(id, filter.id().toString()),
                () -> assertEquals(username, filter.username()),
                () -> assertEquals(from, filter.from().toEpochMilli()),
                () -> assertEquals(to, filter.to().toEpochMilli()),
                () -> assertEquals(size, pageable.getPageSize()),
                () -> assertEquals(page, pageable.getPageNumber())
        );
    }

    @ValueSource(strings = {
            "CLIENT",
            "OPERATOR"
    })
    @ParameterizedTest
    @DisplayName("Доступ должен быть запрещен для ролей отличных от ADMIN.")
    void getClients_shouldReturnForbiddenForNotAdmin(String role) throws Exception {
        mockMvc.perform(get("/api/private/client")
                        .with(user("user").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ValueSource(strings = {
            "OPERATOR", "USER"
    })
    @ParameterizedTest
    @DisplayName("Доступ должен быть запрещен для всех кроме администратора и оператора.")
    void update_shouldReturnForbiddenIfNotAdminOrClient(String role) throws Exception {
        mockMvc.perform(patch("/api/private/client/21c28723-95ab-4349-a918-ec3b0ce26ad4")
                        .with(user("21c28723-95ab-4349-a918-ec3b0ce26ad4").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"BLOCKED\"}",
            "{\"status\":\"BLOCKED\", \"callbackUrl\":\"https://example.com\"}",
            "{\"status\":\"BLOCKED\",\"orderTimeoutSeconds\":500}",
            "{\"status\":\"BLOCKED\",\"orderTimeoutSeconds\":500, \"callbackUrl\":\"https://example.com\"}",
            "{\"callbackUrl\":\"https://example.com\"}",
            "{\"methods\":[\"CARD\",\"SBP\"]}",
            "{\"comment\":\"some comment\",\"minWithdrawalAmount\":5004}"
    })
    @DisplayName("Сериализация должна пройти без ошибок.")
    void update_shouldUpdateClient(String json) throws Exception {
        UpdateClientDTO expected = objectMapper.readValue(json, UpdateClientDTO.class);
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(patch("/api/private/client/" + clientId)
                        .header("Content-Type", "application/json")
                        .with(user("e6230b60-8b5b-4dd7-8c59-e29a827e12f6").roles("ADMIN"))
                        .with(csrf())
                        .content(json))
                .andExpect(status().isOk());
        ArgumentCaptor<UpdateClientDTO> captor = ArgumentCaptor.forClass(UpdateClientDTO.class);
        verify(clientService).update(eq(clientId), captor.capture());
        assertEquals(expected, captor.getValue());
    }

    @Test
    @DisplayName("Метод должен обновить клиента, совершившего запрос.")
    void update_shouldUpdateClientForClientAccess() throws Exception {
        String json = "{\"callbackUrl\":\"https://example.com\"}";
        UpdateClientDTO expected = objectMapper.readValue(json, UpdateClientDTO.class);
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(patch("/api/private/client/" + clientId)
                        .header("Content-Type", "application/json")
                        .with(user("e6230b60-8b5b-4dd7-8c59-e29a827e12f6").roles("ADMIN"))
                        .with(csrf())
                        .content(json))
                .andExpect(status().isOk());
        ArgumentCaptor<UpdateClientDTO> captor = ArgumentCaptor.forClass(UpdateClientDTO.class);
        verify(clientService).update(eq(clientId), captor.capture());
        assertEquals(expected, captor.getValue());
    }
}