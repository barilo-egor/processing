package net.rcetech.billing.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.repository.billing.WithdrawalRequestSpecifications;
import net.rcetech.domain.service.billing.WithdrawalRequestService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.billing.dto.WithdrawalRequestFilter;
import net.rcetech.meta.billing.dto.WithdrawalRequestResponse;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/withdrawal-request")
@SupportAccess
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
    public void update(Principal principal, @PathVariable UUID id, @RequestParam WithdrawalRequestStatus status) {
        withdrawalRequestService.updateStatus(id, status);
        log.info("Сотрудник поддержки {} перевёл заявку на вывод {} в статус {}.", principal.getName(), id, status);
    }
}
