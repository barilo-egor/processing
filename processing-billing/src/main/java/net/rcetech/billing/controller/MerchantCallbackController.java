package net.rcetech.billing.controller;

import net.rcetech.domain.repository.orders.MerchantCallbackSpecifications;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.billing.dto.MerchantCallbackFilter;
import net.rcetech.meta.billing.dto.MerchantCallbackResponse;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/merchant-callback")
@SupportAccess
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
