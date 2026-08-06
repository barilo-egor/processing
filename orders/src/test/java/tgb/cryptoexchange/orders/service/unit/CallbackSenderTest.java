package tgb.cryptoexchange.orders.service.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import tgb.cryptoexchange.orders.dto.ClientDTO;
import tgb.cryptoexchange.orders.dto.OrderDTO;
import tgb.cryptoexchange.orders.service.ApiClientsGrpcService;
import tgb.cryptoexchange.orders.service.CallbackSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallbackSenderTest {

    @Mock
    private ApiClientsGrpcService apiClientsGrpcService;

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

        callbackSender = new CallbackSender(objectMapper, apiClientsGrpcService, webClientBuilder);
    }

    @Test
    @DisplayName("Успешный вызов всей цепочки отправки")
    void handleCreatedEvent_PureUnit_Success() {
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setId(UUID.randomUUID());
        orderDTO.setClientId(1L);
        orderDTO.setCallbackUrl("http://example.com");

        String expectedSignature = "valid-grpc-signature";

        when(apiClientsGrpcService.createSignature(eq(1L), anyString()))
                .thenReturn(Mono.just(expectedSignature));

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.uri(ArgumentMatchers.anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(ArgumentMatchers.any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        callbackSender.handleCreatedEvent(orderDTO);

        verify(requestBodyUriSpec, times(1)).uri("http://example.com");
        verify(requestBodySpec).header("Signature", expectedSignature);
        verify(requestBodySpec).header(eq("X-Timestamp"), anyString());
        verify(requestBodySpec).bodyValue(orderDTO);
        ArgumentCaptor<String> dataToSignCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClientsGrpcService).createSignature(eq(1L), dataToSignCaptor.capture());

        String actualDataToSign = dataToSignCaptor.getValue();
        assertThat(actualDataToSign).startsWith("POST|/|");
    }

    @Test
    @DisplayName("Пропуск отправки, если URL изначально пуст")
    void handleCreatedEvent_PureUnit_BlankUrl_Skips() {
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setId(UUID.randomUUID());
        orderDTO.setClientId(2L);
        orderDTO.setCallbackUrl("");

        ClientDTO mockClientDto = new ClientDTO();
        mockClientDto.setCallbackUrl(" ");
        when(apiClientsGrpcService.getClientById(2L)).thenReturn(Mono.just(mockClientDto));

        callbackSender.handleCreatedEvent(orderDTO);

        verify(apiClientsGrpcService, never()).createSignature(anyLong(), anyString());
        verify(webClient, never()).post();
    }

}