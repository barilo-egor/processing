package net.rcetech.api.service;

import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimitedOrderCreatorTest {

    @Mock
    private OrderApiService orderApiService;

    private CreateOrderCommand command() {
        return new CreateOrderCommand(UUID.randomUUID(), new CreateOrderRequest(UUID.randomUUID().toString(), 1000,
                Set.of(RequestMethod.CARD), null, "1235153208"), 30, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("Если все разрешения заняты асинхронными поисками, создание должно бросать ServiceUnavailableException без вызова сервиса.")
    void createOrder_shouldThrowIfLimitReached(int concurrencyLimit) {
        LimitedOrderCreator creator = new LimitedOrderCreator(orderApiService, concurrencyLimit);
        for (int i = 0; i < concurrencyLimit; i++) {
            creator.createOrder(command(), true);
        }
        clearInvocations(orderApiService);

        assertThrows(ServiceUnavailableException.class, () -> creator.createOrder(command(), false));
        assertThrows(ServiceUnavailableException.class, () -> creator.createOrder(command(), true));
        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("После завершения асинхронного поиска разрешение должно освобождаться.")
    void createOrder_shouldReleasePermitOnAsyncSearchCompleted(int concurrencyLimit) {
        LimitedOrderCreator creator = new LimitedOrderCreator(orderApiService, concurrencyLimit);
        for (int i = 0; i < concurrencyLimit; i++) {
            creator.createOrder(command(), true);
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BiConsumer<Void, Throwable>> captor = ArgumentCaptor.forClass(BiConsumer.class);
        verify(orderApiService, times(concurrencyLimit)).createOrderAsync(any(), captor.capture());

        captor.getAllValues().forEach(onSearchCompleted -> onSearchCompleted.accept(null, null));

        for (int i = 0; i < concurrencyLimit; i++) {
            assertDoesNotThrow(() -> creator.createOrder(command(), true));
        }
        assertThrows(ServiceUnavailableException.class, () -> creator.createOrder(command(), true));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("После синхронного создания разрешение должно освобождаться, в том числе при ошибке.")
    void createOrder_shouldReleasePermitIfSync(int concurrencyLimit) {
        LimitedOrderCreator creator = new LimitedOrderCreator(orderApiService, concurrencyLimit);
        CreateOrderCommand failing = command();
        lenient().when(orderApiService.createOrder(failing))
                .thenThrow(new BadRequestException("Ордер уже существует."));

        for (int i = 0; i < concurrencyLimit + 1; i++) {
            assertDoesNotThrow(() -> creator.createOrder(command(), false));
            assertThrows(BadRequestException.class, () -> creator.createOrder(failing, false));
        }
        for (int i = 0; i < concurrencyLimit; i++) {
            creator.createOrder(command(), true);
        }
        assertThrows(ServiceUnavailableException.class, () -> creator.createOrder(command(), true));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("Если асинхронное создание упало до запуска поиска, разрешение должно освобождаться.")
    void createOrder_shouldReleasePermitIfAsyncCreationFailed(int concurrencyLimit) {
        LimitedOrderCreator creator = new LimitedOrderCreator(orderApiService, concurrencyLimit);
        CreateOrderCommand failing = command();
        lenient().when(orderApiService.createOrderAsync(eq(failing), any()))
                .thenThrow(new BadRequestException("Ордер уже существует."));

        for (int i = 0; i < concurrencyLimit + 1; i++) {
            assertThrows(BadRequestException.class, () -> creator.createOrder(failing, true));
        }
        for (int i = 0; i < concurrencyLimit; i++) {
            creator.createOrder(command(), true);
        }
        assertThrows(ServiceUnavailableException.class, () -> creator.createOrder(command(), true));
    }
}
