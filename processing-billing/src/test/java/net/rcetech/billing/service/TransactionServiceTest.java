package net.rcetech.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import net.rcetech.billing.dto.TransactionDTO;
import net.rcetech.billing.entity.Transaction;
import net.rcetech.billing.mapper.TransactionMapper;
import net.rcetech.billing.repository.TransactionRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("Успешное создание новой transaction")
    void create_ShouldSaveTransaction_WhenIdDoesNotExist() {

        UUID transactionId = UUID.randomUUID();
        TransactionDTO dto = TransactionDTO.builder().id(transactionId).build();
        Transaction entity = Transaction.builder().id(transactionId).build();

        when(transactionRepository.existsById(transactionId)).thenReturn(false);
        when(transactionMapper.toEntity(dto)).thenReturn(entity);
        when(transactionRepository.save(entity)).thenReturn(entity);

        transactionService.create(dto);

        verify(transactionRepository, times(1)).existsById(transactionId);
        verify(transactionMapper, times(1)).toEntity(dto);
        verify(transactionRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("Игнорирование создания, если transaction с таким id уже существует")
    void create_ShouldReturnImmediately_WhenIdAlreadyExists() {
        UUID transactionId = UUID.randomUUID();
        TransactionDTO dto = TransactionDTO.builder().id(transactionId).build();

        when(transactionRepository.existsById(transactionId)).thenReturn(true);

        transactionService.create(dto);

        verify(transactionRepository, times(1)).existsById(transactionId);
        verifyNoInteractions(transactionMapper);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Сохранение transaction")
    void save_ShouldAlwaysSaveTransaction() {
        UUID transactionId = UUID.randomUUID();
        TransactionDTO dto = TransactionDTO.builder().id(transactionId).build();
        Transaction entity = Transaction.builder().id(transactionId).build();

        when(transactionMapper.toEntity(dto)).thenReturn(entity);
        when(transactionRepository.save(entity)).thenReturn(entity);

        transactionService.save(dto);

        verify(transactionMapper, times(1)).toEntity(dto);
        verify(transactionRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("Успешный поиск transaction с пагинацией и маппингом")
    @SuppressWarnings("unchecked")
    void findTransactions_ShouldReturnMappedPage() {
        int page = 0;
        int size = 10;
        List<String> sorters = List.of("id,desc");

        Specification<Transaction> spec = (root, query, criteriaBuilder) -> null;

        Transaction entity = new Transaction();
        TransactionDTO dto = new TransactionDTO();

        Page<Transaction> entityPage = new PageImpl<>(List.of(entity));

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);
        when(transactionMapper.entityToDTO(entity)).thenReturn(dto);

        Page<TransactionDTO> resultPage = transactionService.findTransactions(spec, page, size, sorters);

        assertNotNull(resultPage, "Результат пагинации не должен быть null");
        assertEquals(1, resultPage.getTotalElements(), "Количество элементов на странице не совпадает");
        assertEquals(dto, resultPage.getContent().getFirst(), "Элемент страницы не смаппился в DTO");

        verify(transactionRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(transactionMapper, times(1)).entityToDTO(entity);
    }

}