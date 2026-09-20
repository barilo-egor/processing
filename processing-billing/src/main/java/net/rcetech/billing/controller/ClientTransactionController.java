package net.rcetech.billing.controller;

import net.rcetech.domain.repository.billing.TransactionSpecification;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.dto.ClientTransactionFilter;
import net.rcetech.meta.billing.dto.ClientTransactionResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.V1_API_PATH + "/transaction")
@PreAuthorize("hasRole('CLIENT')")
public class ClientTransactionController {

    private final TransactionService transactionService;

    public ClientTransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public PagedModel<ClientTransactionResponse> getOrders(ClientTransactionFilter filter,
                                                           @PageableDefault(size = 20) Pageable pageable,
                                                           Principal principal) {
        return new PagedModel<>(transactionService.findAll(
                TransactionSpecification.matches(UUID.fromString(principal.getName()), filter),
                pageable,
                ClientTransactionResponse.class
        ));
    }
}
