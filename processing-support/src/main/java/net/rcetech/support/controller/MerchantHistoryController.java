package net.rcetech.support.controller;

import net.rcetech.meta.WebPath;
import net.rcetech.meta.support.dto.MerchantHistoryFilter;
import net.rcetech.meta.support.dto.MerchantHistoryResponseDTO;
import net.rcetech.support.service.MerchantHistoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/merchant-history")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class MerchantHistoryController {

    private final MerchantHistoryService merchantHistoryService;

    public MerchantHistoryController(MerchantHistoryService merchantHistoryService) {
        this.merchantHistoryService = merchantHistoryService;
    }

    @GetMapping
    public ResponseEntity<List<MerchantHistoryResponseDTO>> get(
            MerchantHistoryFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(merchantHistoryService.getHistory(filter, pageable));
    }

}
