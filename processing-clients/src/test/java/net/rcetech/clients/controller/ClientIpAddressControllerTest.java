package net.rcetech.clients.controller;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.clients.IpAddressService;
import net.rcetech.meta.clients.dto.IpAddressResponseDTO;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientIpAddressController.class)
@Import(MetaSecurityConfig.class)
class ClientIpAddressControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private IpAddressService ipAddressService;

    @MockitoBean
    private ClientService clientService;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,192.168.1.1,1",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,2001:db8:0:0:0:0:0:1,27"
    })
    @DisplayName("Метод должен добавить адрес клиенту из контекста и вернуть 201 с адресом.")
    void create_shouldReturnCreatedAddress(UUID clientId, String address, Long id) throws Exception {
        Client client = mockClient(clientId);
        when(ipAddressService.create(address, client)).thenReturn(new IpAddressResponseDTO(id, address));

        mockMvc.perform(post("/api/client/ip-address")
                        .queryParam("address", address)
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.address").value(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("Метод должен вернуть 400, если адрес пуст.")
    void create_shouldReturnBadRequestIfAddressBlank(String address) throws Exception {
        UUID clientId = UUID.randomUUID();
        mockClient(clientId);

        mockMvc.perform(post("/api/client/ip-address")
                        .queryParam("address", address)
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isBadRequest());

        verify(ipAddressService, never()).create(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Некорректный IP-адрес abc.", "IP-адрес 10.0.0.1 уже добавлен."})
    @DisplayName("Метод должен вернуть 400, если сервис выбросил BadRequestException.")
    void create_shouldReturnBadRequestIfServiceThrows(String message) throws Exception {
        UUID clientId = UUID.randomUUID();
        Client client = mockClient(clientId);
        when(ipAddressService.create("10.0.0.1", client)).thenThrow(new BadRequestException(message));

        mockMvc.perform(post("/api/client/ip-address")
                        .queryParam("address", "10.0.0.1")
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(message));
    }

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,192.168.1.1,10.0.0.1",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,2001:db8:0:0:0:0:0:1,172.16.0.1"
    })
    @DisplayName("Метод должен вернуть адреса клиента из контекста.")
    void getAll_shouldReturnClientAddresses(UUID clientId, String first, String second) throws Exception {
        Client client = mockClient(clientId);
        when(ipAddressService.findAllByClientId(client)).thenReturn(List.of(
                new IpAddressResponseDTO(1L, first), new IpAddressResponseDTO(2L, second)));

        mockMvc.perform(get("/api/client/ip-address")
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].address").value(first))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].address").value(second));
    }

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,1",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,42"
    })
    @DisplayName("Метод должен удалить адрес клиента из контекста.")
    void delete_shouldDeleteAddress(UUID clientId, Long id) throws Exception {
        Client client = mockClient(clientId);

        mockMvc.perform(delete("/api/client/ip-address/" + id)
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isOk());

        verify(ipAddressService).delete(client, id);
    }

    @ParameterizedTest
    @ValueSource(longs = {5, 77})
    @DisplayName("Метод должен вернуть 400, если адрес не найден у клиента.")
    void delete_shouldReturnBadRequestIfServiceThrows(Long id) throws Exception {
        UUID clientId = UUID.randomUUID();
        Client client = mockClient(clientId);
        String message = "Запись с идентификатором " + id + " не найдена.";
        doThrow(new BadRequestException(message)).when(ipAddressService).delete(client, id);

        mockMvc.perform(delete("/api/client/ip-address/" + id)
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(message));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = {"CLIENT"})
    @DisplayName("Методы должны быть запрещены для всех ролей кроме клиента.")
    void allMethods_shouldReturnForbiddenIfNotClient(Role role) throws Exception {
        String userId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/client/ip-address").queryParam("address", "10.0.0.1")
                        .with(csrf()).with(user(userId).roles(role.name())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/client/ip-address").with(user(userId).roles(role.name())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/client/ip-address/1").with(csrf()).with(user(userId).roles(role.name())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ipAddressService);
    }

    private Client mockClient(UUID clientId) {
        Client client = new Client();
        client.setId(clientId);
        when(clientService.findById(clientId)).thenReturn(Optional.of(client));
        return client;
    }
}
