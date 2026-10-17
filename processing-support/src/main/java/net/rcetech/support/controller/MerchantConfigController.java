package net.rcetech.support.controller;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import net.rcetech.meta.user.AdminAccess;
import net.rcetech.support.service.MerchantConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/merchant-config")
public class MerchantConfigController {

    private final MerchantConfigService merchantConfigService;

    public MerchantConfigController(MerchantConfigService merchantConfigService) {
        this.merchantConfigService = merchantConfigService;
    }

    @GetMapping("/{clientId}")
    @AdminAccess
    public ResponseEntity<List<MerchantConfigResponseDTO>> get(@PathVariable UUID clientId) {
        return ResponseEntity.ok(merchantConfigService.findAll(clientId));
    }

    @PatchMapping("/{id}")
    @AdminAccess
    public ResponseEntity<MerchantConfigResponseDTO> update(Principal principal, @PathVariable Long id,
            @RequestBody MerchantConfigUpdateDTO updateDTO) {
        MerchantConfigResponseDTO merchantConfig = merchantConfigService.update(id, updateDTO);
        log.info("Сотрудник поддержки {} обновил настройку мерчанта {}: {}.", principal.getName(), id, updateDTO);
        return ResponseEntity.ok(merchantConfig);
    }
}
