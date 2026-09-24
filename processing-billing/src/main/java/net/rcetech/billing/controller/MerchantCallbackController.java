package net.rcetech.billing.controller;

import net.rcetech.domain.repository.orders.MerchantCallbackSpecifications;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.dto.MerchantCallbackFilter;
import net.rcetech.meta.billing.dto.MerchantCallbackResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/merchant-callback")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class MerchantCallbackController {

    private final MerchantCallbackService merchantCallbackService;

    public MerchantCallbackController(MerchantCallbackService merchantCallbackService) {
        this.merchantCallbackService = merchantCallbackService;
    }

    @GetMapping
    public PagedModel<MerchantCallbackResponse> get(MerchantCallbackFilter filter, Pageable pageable) {
        return new PagedModel<>(merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(filter), pageable, MerchantCallbackResponse.class
        ));
    }
}
