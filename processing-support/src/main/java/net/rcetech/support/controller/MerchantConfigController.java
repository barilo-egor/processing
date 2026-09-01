package net.rcetech.support.controller;

import net.rcetech.meta.WebPath;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import net.rcetech.support.service.MerchantConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(WebPath.PRIVATE_API_PATH + "/merchant-config")
public class MerchantConfigController {

    private final MerchantConfigService merchantConfigService;

    public MerchantConfigController(MerchantConfigService merchantConfigService) {
        this.merchantConfigService = merchantConfigService;
    }

    @GetMapping("/{ownerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MerchantConfigResponseDTO>> get(@PathVariable UUID ownerId) {
        return ResponseEntity.ok(merchantConfigService.findAll(ownerId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MerchantConfigResponseDTO> update(@PathVariable Long id,
            @RequestBody MerchantConfigUpdateDTO updateDTO) {
        return ResponseEntity.ok(merchantConfigService.update(id, updateDTO));
    }
}
