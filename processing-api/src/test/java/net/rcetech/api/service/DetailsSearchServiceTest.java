package net.rcetech.api.service;

import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.merchantdetails.ApiDetailsResponse;
import net.rcetech.api.merchantdetails.MerchantDetailsReceiver;
import net.rcetech.api.merchantdetails.RandomDetailsReceiver;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetailsSearchServiceTest {

    @Mock
    private MerchantDetailsReceiver merchantDetailsReceiver;

    @Mock
    private RandomDetailsReceiver randomDetailsReceiver;

    private DetailsSearchService detailsSearchService;

    @BeforeEach
    void setUp() {
        detailsSearchService = new DetailsSearchService(merchantDetailsReceiver, randomDetailsReceiver);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 120})
    @DisplayName("Для обычного ордера реквизиты должны запрашиваться в merchant-details.")
    void search_shouldRequestMerchantDetailsForNotTestOrder(int timeoutSeconds) {
        Order order = order();
        CreateOrderRequest request = request();
        Optional<ApiDetailsResponse> expected = Optional.of(detailsResponse());
        when(merchantDetailsReceiver.getDetails(order.getClient().getId(), order.getId(), request, timeoutSeconds))
                .thenReturn(expected);

        assertEquals(expected, detailsSearchService.search(order,
                new CreateOrderCommand(order.getClient().getId(), request, timeoutSeconds, false)));
        verifyNoInteractions(randomDetailsReceiver);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 120})
    @DisplayName("Для тестового ордера реквизиты должны генерироваться без запроса в merchant-details.")
    void search_shouldGenerateDetailsForTestOrder(int timeoutSeconds) {
        Order order = order();
        CreateOrderRequest request = request();
        Optional<ApiDetailsResponse> expected = Optional.of(detailsResponse());
        when(randomDetailsReceiver.getDetails(order.getClient().getId(), order.getId(), request, timeoutSeconds))
                .thenReturn(expected);

        assertEquals(expected, detailsSearchService.search(order,
                new CreateOrderCommand(order.getClient().getId(), request, timeoutSeconds, true)));
        verifyNoInteractions(merchantDetailsReceiver);
    }

    private Order order() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        return order;
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest(UUID.randomUUID().toString(), 1000, Set.of(RequestMethod.CARD), null,
                "1235153208");
    }

    private ApiDetailsResponse detailsResponse() {
        return new ApiDetailsResponse(UUID.randomUUID().toString(), Merchant.ALFA_TEAM, UUID.randomUUID().toString(),
                "NEW", null, new ApiDetailsResponse.Details(RequestMethod.CARD, "1234 1234 1234 1234", "Т-Банк"));
    }
}
