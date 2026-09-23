package net.rcetech.billing.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import net.rcetech.domain.repository.billing.WithdrawalRequestSpecifications;
import net.rcetech.domain.service.billing.WithdrawalRequestService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.dto.ClientWithdrawalRequestResponse;
import net.rcetech.meta.billing.dto.WithdrawalRequestFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.V1_API_PATH + "/withdrawal-request")
@PreAuthorize("hasRole('CLIENT')")
public class ClientWithdrawalRequestController {

    private final WithdrawalRequestService withdrawalRequestService;

    public ClientWithdrawalRequestController(WithdrawalRequestService withdrawalRequestService) {
        this.withdrawalRequestService = withdrawalRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(Principal principal,
                       @RequestParam @Positive Integer amount,
                       @RequestParam @NotBlank String address) {
        withdrawalRequestService.create(UUID.fromString(principal.getName()), amount, address);
    }

    @GetMapping
    public PagedModel<ClientWithdrawalRequestResponse> get(Principal principal, WithdrawalRequestFilter filter,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(UUID.fromString(principal.getName()), filter),
                pageable,
                ClientWithdrawalRequestResponse.class
        ));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void cancel(Principal principal, @PathVariable UUID id) {
        withdrawalRequestService.cancel(UUID.fromString(principal.getName()), id);
    }
}
