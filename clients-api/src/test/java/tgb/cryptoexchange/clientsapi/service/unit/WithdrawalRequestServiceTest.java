package tgb.cryptoexchange.clientsapi.service.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tgb.cryptoexchange.clientsapi.dto.WithdrawalRequestDTO;
import tgb.cryptoexchange.clientsapi.entity.WithdrawalRequest;
import tgb.cryptoexchange.clientsapi.exceptions.FieldNotBeEmptyException;
import tgb.cryptoexchange.clientsapi.exceptions.NotFoundException;
import tgb.cryptoexchange.clientsapi.mapper.WithdrawalMapper;
import tgb.cryptoexchange.clientsapi.repository.WithdrawalRequestRepository;
import tgb.cryptoexchange.clientsapi.service.WithdrawalRequestService;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalRequestServiceTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private WithdrawalMapper mapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WithdrawalRequestService withdrawalRequestService;

    @Test
    @DisplayName("Сохранение заявки публикует событие, если eventPublisher доступен")
    void should_saveWithdrawalRequestAndPublishEvent_when_eventPublisherIsPresent() {
        WithdrawalRequestDTO inputDto = new WithdrawalRequestDTO();
        WithdrawalRequest entity = new WithdrawalRequest();
        WithdrawalRequest savedEntity = new WithdrawalRequest();
        WithdrawalRequestDTO resultDto = new WithdrawalRequestDTO();

        when(mapper.requestDTOToEntity(inputDto)).thenReturn(entity);
        when(withdrawalRequestRepository.save(entity)).thenReturn(savedEntity);
        when(mapper.entityToDTO(savedEntity)).thenReturn(resultDto);

        withdrawalRequestService.saveWithdrawalRequest(inputDto);

        verify(withdrawalRequestRepository, times(1)).save(entity);
        verify(eventPublisher, times(1)).publishEvent(resultDto);
    }

    @Test
    @DisplayName("Сохранение заявки не падает, если eventPublisher равен null")
    void should_saveWithdrawalRequestWithoutPublishing_when_eventPublisherIsNull() {
        WithdrawalRequestService serviceWithoutPublisher = new WithdrawalRequestService(
                withdrawalRequestRepository, mapper, null
        );

        WithdrawalRequestDTO inputDto = new WithdrawalRequestDTO();
        WithdrawalRequest entity = new WithdrawalRequest();
        WithdrawalRequest savedEntity = new WithdrawalRequest();
        WithdrawalRequestDTO resultDto = new WithdrawalRequestDTO();

        when(mapper.requestDTOToEntity(inputDto)).thenReturn(entity);
        when(withdrawalRequestRepository.save(entity)).thenReturn(savedEntity);
        when(mapper.entityToDTO(savedEntity)).thenReturn(resultDto);

        assertDoesNotThrow(() -> serviceWithoutPublisher.saveWithdrawalRequest(inputDto));

        verify(withdrawalRequestRepository, times(1)).save(entity);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Обновление полей существующей заявки")
    void should_updateWithdrawalRequestFields_when_idAndRequestAreValid() {
        Long requestId = 1L;
        WithdrawalRequestDTO updateDto = new WithdrawalRequestDTO();
        updateDto.setWallet("new_wallet_address");
        updateDto.setComment("New updated comment");

        WithdrawalRequest existingEntity = new WithdrawalRequest();
        existingEntity.setWallet("old_wallet");
        existingEntity.setComment("Old comment");

        when(withdrawalRequestRepository.findById(requestId)).thenReturn(Optional.of(existingEntity));

        withdrawalRequestService.updateWithdrawalRequest(requestId, updateDto);

        assertEquals("new_wallet_address", existingEntity.getWallet());
        assertEquals("New updated comment", existingEntity.getComment());
    }

    @Test
    @DisplayName("Обновление заявки падает с FieldNotBeEmptyException, если id равен null")
    void should_throwFieldNotBeEmptyException_when_idIsNull() {
        WithdrawalRequestDTO updateDto = new WithdrawalRequestDTO();

        FieldNotBeEmptyException exception = assertThrows(FieldNotBeEmptyException.class, () ->
                withdrawalRequestService.updateWithdrawalRequest(null, updateDto)
        );
        assertEquals("Should not be empty.", exception.getDescription());
        verifyNoInteractions(withdrawalRequestRepository);
    }

    @Test
    @DisplayName("Обновление заявки падает с NotFoundException, если ID не найден в БД")
    void should_throwNotFoundException_when_requestDoesNotExist() {
        Long nonExistingId = 999L;
        WithdrawalRequestDTO updateDto = new WithdrawalRequestDTO();

        when(withdrawalRequestRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                withdrawalRequestService.updateWithdrawalRequest(nonExistingId, updateDto)
        );
        assertEquals("Record not found for the provided ID.", exception.getDescription());
    }

}