package net.rcetech.orders.service;

import tools.jackson.databind.ObjectMapper;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateSignatureDTO;
import net.rcetech.clients.service.ClientApi;
import net.rcetech.orders.dto.OrderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallbackSenderTest {

    @Mock
    private ClientApi clientApi;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private CallbackSender callbackSender;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        WebClient.Builder webClientBuilder = mock(WebClient.Builder.class);
        when(webClientBuilder.build()).thenReturn(webClient);

        callbackSender = new CallbackSender(objectMapper, clientApi, webClientBuilder);
    }

    @Test
    @DisplayName("Успешный вызов всей цепочки отправки")
    void handleCreatedEvent_PureUnit_Success() {
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setId(UUID.randomUUID());
        orderDTO.setClientId(1L);
        orderDTO.setCallbackUrl("http://example.com");

        String expectedSignature = "valid-signature-string";

        when(clientApi.createSignature(any(CreateSignatureDTO.class)))
                .thenReturn(expectedSignature);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(ArgumentMatchers.any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        callbackSender.handleCreatedEvent(orderDTO);

        verify(requestBodyUriSpec, times(1)).uri("http://example.com");
        verify(requestBodySpec).header("Signature", expectedSignature);
        verify(requestBodySpec).header(eq("X-Timestamp"), anyString());
        verify(requestBodySpec).bodyValue(orderDTO);

        ArgumentCaptor<CreateSignatureDTO> signatureDtoCaptor = ArgumentCaptor.forClass(CreateSignatureDTO.class);
        verify(clientApi).createSignature(signatureDtoCaptor.capture());

        CreateSignatureDTO capturedDto = signatureDtoCaptor.getValue();
        assertThat(capturedDto.clientId()).isEqualTo(1L);
        assertThat(capturedDto.data()).startsWith("POST|/|");
    }

    @Test
    @DisplayName("Пропуск отправки, если URL изначально пуст")
    void handleCreatedEvent_PureUnit_BlankUrl_Skips() {
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setId(UUID.randomUUID());
        orderDTO.setClientId(2L);
        orderDTO.setCallbackUrl("");

        ClientResponseDTO mockClientDto = new ClientResponseDTO(
                2L,
                "test_user",
                "secret",
                "preview",
                Instant.now(),
                "ACTIVE",
                " ",
                300
        );
        when(clientApi.getClientById(2L)).thenReturn(mockClientDto);

        callbackSender.handleCreatedEvent(orderDTO);

        verify(clientApi, never()).createSignature(any());
        verify(webClient, never()).post();
    }

}