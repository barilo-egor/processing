package net.rcetech.clients.controller;

import net.rcetech.clients.config.ClientsSecurityConfig;
import net.rcetech.clients.event.KeycloakEvent;
import net.rcetech.clients.service.KeycloakEventService;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import({ClientsSecurityConfig.class, MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ClientControllerTest {

    @MockitoBean
    private KeycloakEventService keycloakEventService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ClientService clientService;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @DisplayName("Сериализация объекта должна пройти без ошибок, должен быть вызван метод сервиса.")
    @ValueSource(strings = {"login_event.json", "login_error_event.json", "refresh_token_event.json",
            "login_event_with_unknown_field.json"})
    @WithMockUser(roles = {"WEBHOOK_CLIENT"})
    void event_shouldSerializeJson(String fileName) throws Exception {
        String json = new String(new ClassPathResource("/controller/keycloak/" + fileName).getInputStream().readAllBytes());
        mockMvc.perform(
                        post("/client/event/")
                                .header("Content-Type", "application/json")
                                .content(json))
                .andExpect(status().isCreated());
        verify(keycloakEventService).handle(any(KeycloakEvent.class));
    }

    @Test
    @WithMockUser
    void getClients_shouldReturnEmptyArray() throws Exception {
        Page<ClientResponseDTO> page = new PageImpl<>(new ArrayList<>());
        when(clientService.findAll(any(), any())).thenReturn(page);
        mockMvc.perform(get("/client")
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
    @WithMockUser
    void getClients_shouldReturnClients(int clientsSize) throws Exception {
        List<ClientResponseDTO> clients = new ArrayList<>();
        for (int i = 0; i < clientsSize; i++) {
            ClientResponseDTO client = getClient(i);
            clients.add(client);
        }
        Page<ClientResponseDTO> page = new PageImpl<>(
                clients, PageRequest.of(0, 100), clients.size()
        );
        when(clientService.findAll(any(), any())).thenReturn(page);
        ResultActions resultActions = mockMvc.perform(get("/client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        int i = 0;
        for (ClientResponseDTO client : clients) {
            resultActions.andExpect(jsonPath("$.content[" + i + "].id").value(client.id().toString()));
            resultActions.andExpect(jsonPath("$.content[" + i + "].username").value(client.username()));
            resultActions.andExpect(jsonPath("$.content[" + i + "].registeredAt").value(client.registeredAt().toEpochMilli()));
            resultActions.andExpect(jsonPath("$.content[" + i + "].status").value("ACTIVE"));
            resultActions.andExpect(jsonPath("$.content[" + i + "].callbackUrl").value("https://example.com/callback"));
            resultActions.andExpect(jsonPath("$.content[" + i + "].orderTimeoutSeconds").value(900));
            i++;
        }
    }

    private static @NonNull ClientResponseDTO getClient(int i) {
        return new ClientResponseDTO(UUID.randomUUID(), "test" + i, Instant.now(),
                ClientStatus.ACTIVE, "https://example.com/callback", 900);
    }

    @CsvSource("""
            b8ee52fd-e7ed-4004-8c1f-955a76719685,admin,ACTIVE,1787659000000,1787659000123,10,3
            c6e5d372-9436-4cdb-a6c4-dc33c0fc8d7a,user_poser,BLOCKED,1787659000000,1787659000987,20,10
            """)
    @ParameterizedTest
    @WithMockUser
    void getClients_shouldPassParametersToMethod(String id, String username, String status, long from, long to,
                                                 int size, int page) throws Exception {
        when(clientService.findAll(any(), any())).thenReturn(new PageImpl<>(new ArrayList<>()));
        ArgumentCaptor<ClientFilter> filterCaptor = ArgumentCaptor.forClass(ClientFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        mockMvc.perform(get("/client")
                .queryParam("id", id)
                .queryParam("username", username)
                .queryParam("status", status)
                .queryParam("from", String.valueOf(from))
                .queryParam("to", String.valueOf(to))
                .queryParam("page", String.valueOf(page))
                .queryParam("size", String.valueOf(size))
        ).andExpect(status().isOk());
        verify(clientService).findAll(filterCaptor.capture(), pageableCaptor.capture());
        ClientFilter filter = filterCaptor.getValue();
        Pageable pageable = pageableCaptor.getValue();
        assertAll(
                () -> assertEquals(id, filter.id().toString()),
                () -> assertEquals(username, filter.username()),
                () -> assertEquals(status, filter.status().name()),
                () -> assertEquals(from, filter.from().toEpochMilli()),
                () -> assertEquals(to, filter.to().toEpochMilli()),
                () -> assertEquals(size, pageable.getPageSize()),
                () -> assertEquals(page, pageable.getPageNumber())
        );
    }
}