package net.rcetech.billing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.rcetech.meta.billing.dto.CreateTransactionRequest;
import net.rcetech.meta.billing.dto.GetTransactionsRequest;
import net.rcetech.meta.billing.dto.GetTransactionsResponse;
import net.rcetech.meta.billing.dto.TransactionDTO;
import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.mapper.billing.TransactionMapper;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.billing.utils.TransactionSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    private final TransactionMapper transactionRequestMapper;

    /**
     * Регистрирует новую транзакцию в системе.
     * Идемпотентный эндпоинт, гарантирующий защиту от дубликатов по ID.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionDTO dto = transactionRequestMapper.toDTO(request);
        transactionService.create(dto);
    }

    /**
     * Возвращает страницу транзакций с поддержкой фильтрации, пагинации и сортировки.
     */
    @GetMapping
    public GetTransactionsResponse getTransactions(@Valid @ModelAttribute GetTransactionsRequest request) {
        Specification<Transaction> spec = TransactionSpecification.buildSpecification(request);

        Page<TransactionDTO> pageResult = transactionService.findTransactions(
                spec,
                request.page(),
                request.size(),
                request.sorters()
        );

        return GetTransactionsResponse.builder()
                .transactions(pageResult.getContent())
                .totalElements(pageResult.getTotalElements())
                .build();
    }

}
