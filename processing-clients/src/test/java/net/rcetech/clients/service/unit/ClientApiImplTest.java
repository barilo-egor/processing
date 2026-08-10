package net.rcetech.clients.service.unit;

import net.rcetech.clients.dto.ClientDTO;
import net.rcetech.clients.exceptions.UserNotFoundException;
import net.rcetech.clients.mapper.ClientMapper;
import net.rcetech.clients.service.ClientApiImpl;
import net.rcetech.clients.service.ClientService;
import net.rcetech.clientsapi.dto.ClientResponseDTO;
import net.rcetech.clientsapi.dto.CreateSignatureDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientApiImplTest {

    @Mock
    private ClientService clientService;

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private ClientApiImpl clientApi;

    @Test
    @DisplayName("getClientById: Успешное получение и маппинг клиента")
    void getClientById_Success() {
        long clientId = 100L;
        ClientDTO internalDto = new ClientDTO();
        ClientResponseDTO expectedResponse = new ClientResponseDTO(
                clientId, "test_user", "secret", "preview",
                Instant.now(), "ACTIVE", "http://callback", 300
        );

        when(clientService.getClientById(clientId)).thenReturn(internalDto);
        when(clientMapper.toClientResponseDTO(internalDto)).thenReturn(expectedResponse);

        ClientResponseDTO actualResponse = clientApi.getClientById(clientId);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        verify(clientService, times(1)).getClientById(clientId);
        verify(clientMapper, times(1)).toClientResponseDTO(internalDto);
    }

    @Test
    @DisplayName("getClientById: Выброс UserNotFoundException, если клиент не найден")
    void getClientById_NotFound() {
        long clientId = 404L;
        when(clientService.getClientById(clientId)).thenThrow(new UserNotFoundException());

        assertThrows(UserNotFoundException.class, () -> clientApi.getClientById(clientId));

        verify(clientService, times(1)).getClientById(clientId);
        verifyNoInteractions(clientMapper);
    }

    @Test
    @DisplayName("createSignature: Успешная генерация подписи")
    void createSignature_Success() {
        Long clientId = 777L;
        String rawData = "some_payload_data";
        String expectedSignature = "signature_hash_string";
        CreateSignatureDTO requestDto = new CreateSignatureDTO(clientId, rawData);

        when(clientService.createSignature(clientId, rawData)).thenReturn(expectedSignature);

        String actualSignature = clientApi.createSignature(requestDto);

        assertNotNull(actualSignature);
        assertEquals(expectedSignature, actualSignature);

        verify(clientService, times(1)).createSignature(clientId, rawData);
    }

    @Test
    @DisplayName("createSignature: Ошибка UserNotFoundException при создании подписи")
    void createSignature_NotFound() {
        Long clientId = 404L;
        String rawData = "data";
        CreateSignatureDTO requestDto = new CreateSignatureDTO(clientId, rawData);

        when(clientService.createSignature(clientId, rawData)).thenThrow(new UserNotFoundException());

        assertThrows(UserNotFoundException.class, () -> clientApi.createSignature(requestDto));

        verify(clientService, times(1)).createSignature(clientId, rawData);
    }

}
