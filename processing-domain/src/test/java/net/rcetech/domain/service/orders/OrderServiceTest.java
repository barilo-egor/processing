package net.rcetech.domain.service.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import net.rcetech.meta.orders.dto.OrderDTO;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.exception.AlreadyExistsException;
import net.rcetech.meta.orders.exception.NotFoundException;
import net.rcetech.domain.mapper.orders.OrderMapper;
import net.rcetech.domain.repository.orders.OrderRepository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private net.rcetech.domain.service.orders.OrderService orderService;

    @Mock
    private Specification<Order> mockSpec;

    @Test
    @DisplayName("Успешное создание заказа со статусом NEW и генерацией UUID")
    void create_Success() {
        OrderDTO inputDto = new OrderDTO();
        inputDto.setId(UUID.randomUUID());
        inputDto.setInternalId("internal-123");
        inputDto.setClientId(1L);
        inputDto.setAmount(10);
        inputDto.setEnableUniqueAmount(true);
        inputDto.setCallbackUrl("http://localhost/callback");

        Order savedEntity = Order.builder()
                .id(UUID.randomUUID())
                .internalId("internal-123")
                .status(OrderStatus.NEW)
                .build();

        OrderDTO expectedDto = new OrderDTO();
        expectedDto.setId(savedEntity.getId());
        expectedDto.setStatus(OrderStatus.NEW);

        when(orderRepository.existsByInternalId("internal-123")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenReturn(savedEntity);
        when(orderMapper.entityToDTO(savedEntity)).thenReturn(expectedDto);

        OrderDTO resultDto = orderService.create(inputDto);

        assertThat(resultDto).isNotNull();
        assertThat(resultDto.getId()).isEqualTo(savedEntity.getId());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getId()).isNotNull();
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(capturedOrder.getInternalId()).isEqualTo("internal-123");
    }

    @Test
    @DisplayName("Выброс AlreadyExistsException, если internalId уже зарегистрирован")
    void create_ThrowsAlreadyExistsException() {
        OrderDTO inputDto = new OrderDTO();
        inputDto.setInternalId("duplicate-id");

        when(orderRepository.existsByInternalId("duplicate-id")).thenReturn(true);

        assertThatThrownBy(() -> orderService.create(inputDto))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessageContaining("Bad request.");

        verify(orderRepository, never()).save(any(Order.class));

    }

    @Test
    @DisplayName("Успешное обновление статуса и публикация события")
    void updateStatus_Success() {
        UUID orderId = UUID.randomUUID();
        OrderStatus newStatus = OrderStatus.SUCCESS;

        Order mockOrder = new Order();
        OrderDTO mockDto = new OrderDTO();
        mockDto.setId(orderId);
        mockDto.setStatus(newStatus);

        when(orderRepository.updateStatusById(orderId, newStatus)).thenReturn(1);
        when(orderRepository.getOrdersById(orderId)).thenReturn(mockOrder);
        when(orderMapper.entityToDTO(mockOrder)).thenReturn(mockDto);

        orderService.updateStatus(orderId.toString(), newStatus);

        verify(orderRepository).updateStatusById(orderId, newStatus);
        verify(eventPublisher).publishEvent(mockDto);
    }

    @Test
    @DisplayName("Выброс NotFoundException, если запись в БД не найдена (обновлено 0 строк)")
    void updateStatus_ThrowsNotFoundException() {
        UUID orderId = UUID.randomUUID();
        OrderStatus newStatus = OrderStatus.SUCCESS;

        when(orderRepository.updateStatusById(orderId, newStatus)).thenReturn(0);

        String id = orderId.toString();

        assertThatThrownBy(() -> orderService.updateStatus(id, newStatus))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Record not found for the provided ID.");

        verify(eventPublisher, never()).publishEvent(any());
        verify(orderRepository, never()).getOrdersById(any());
    }

    @Test
    @DisplayName("Успешный поиск заказов с пагинацией и маппингом в DTO")
    void findOrders_ReturnsPageOfDTOs() {
        Order order = new Order();
        OrderDTO dto = new OrderDTO();
        List<Order> ordersList = Collections.singletonList(order);
        Page<Order> orderPage = new PageImpl<>(ordersList);

        when(orderRepository.findAll(eq(mockSpec), any(Pageable.class))).thenReturn(orderPage);
        when(orderMapper.entityToDTO(order)).thenReturn(dto);

        Page<OrderDTO> resultPage = orderService.findOrders(mockSpec, 0, 10, List.of("amount,desc"));

        assertThat(resultPage).isNotNull();
        assertThat(resultPage.getContent()).hasSize(1);
        assertThat(resultPage.getContent().getFirst()).isEqualTo(dto);
    }

    @Test
    @DisplayName("Успешный поиск списка сущностей по спецификации без пагинации")
    void findOrderByField_ReturnsListOfEntities() {
        Order order = new Order();
        List<Order> expectedList = List.of(order);

        when(orderRepository.findAll(mockSpec)).thenReturn(expectedList);

        List<Order> actualList = orderService.findOrderByField(mockSpec);

        assertThat(actualList)
                .hasSize(1)
                .containsExactly(order);
        verify(orderMapper, never()).entityToDTO(any());
    }

}

