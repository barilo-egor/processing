package net.rcetech.support.controller;

import net.rcetech.meta.WebPath;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponse;
import net.rcetech.meta.user.SupportAccess;
import net.rcetech.support.service.MerchantHistoryService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/merchant-history")
@SupportAccess
public class MerchantHistoryController {

    private final MerchantHistoryService merchantHistoryService;

    public MerchantHistoryController(MerchantHistoryService merchantHistoryService) {
        this.merchantHistoryService = merchantHistoryService;
    }

    @GetMapping
    public PagedModel<MerchantHistoryResponse> get(
            MerchantHistoryFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(new PageImpl<>(merchantHistoryService.getHistory(filter, pageable)));
    }

}
