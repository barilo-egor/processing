package net.rcetech.billing.controller;

import jakarta.validation.Valid;
import net.rcetech.billing.utils.TransactionSpecification;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.dto.ManualCorrectTransaction;
import net.rcetech.meta.billing.dto.TransactionFilter;
import net.rcetech.meta.billing.dto.TransactionResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/transaction")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public PagedModel<TransactionResponse> getTransactions(TransactionFilter filter,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(transactionService.findAll(
                TransactionSpecification.matches(filter), pageable, TransactionResponse.class)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void createManualCorrect(Principal principal, @Valid @RequestBody ManualCorrectTransaction manualCorrectTransaction) {
        transactionService.createManualCorrect(UUID.fromString(principal.getName()), manualCorrectTransaction);
    }

}
