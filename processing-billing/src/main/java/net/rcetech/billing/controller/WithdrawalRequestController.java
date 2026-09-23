package net.rcetech.billing.controller;

import net.rcetech.domain.repository.billing.WithdrawalRequestSpecifications;
import net.rcetech.domain.service.billing.WithdrawalRequestService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.billing.dto.WithdrawalRequestFilter;
import net.rcetech.meta.billing.dto.WithdrawalRequestResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/withdrawal-request")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class WithdrawalRequestController {

    private final WithdrawalRequestService withdrawalRequestService;

    public WithdrawalRequestController(WithdrawalRequestService withdrawalRequestService) {
        this.withdrawalRequestService = withdrawalRequestService;
    }

    @GetMapping
    public PagedModel<WithdrawalRequestResponse> get(Pageable pageable, WithdrawalRequestFilter filter) {
        return new PagedModel<>(withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(null, filter),
                pageable,
                WithdrawalRequestResponse.class
        ));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void update(@PathVariable UUID id, @RequestParam WithdrawalRequestStatus status) {
        withdrawalRequestService.updateStatus(id, status);
    }
}
