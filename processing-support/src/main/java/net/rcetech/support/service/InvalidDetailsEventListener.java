package net.rcetech.support.service;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.incidents.InvalidMerchantDetailsEvent;
import net.rcetech.meta.orders.InvalidDetailsReceivedEvent;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Отключает клиенту мерчанта, выдавшего некорректные реквизиты, и создаёт инцидент.
 */
@Slf4j
@Component
public class InvalidDetailsEventListener {

    private final MerchantConfigService merchantConfigService;

    private final ApplicationEventPublisher eventPublisher;

    public InvalidDetailsEventListener(MerchantConfigService merchantConfigService,
                                       ApplicationEventPublisher eventPublisher) {
        this.merchantConfigService = merchantConfigService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void invalidDetailsReceived(InvalidDetailsReceivedEvent event) {
        String switchResult = disableMerchant(event.clientId(), event.merchant());
        eventPublisher.publishEvent(new InvalidMerchantDetailsEvent(event.orderId(), event.clientId(),
                event.merchant(), event.reason(), switchResult, event.errorTimestamp()));
    }

    private String disableMerchant(UUID clientId, Merchant merchant) {
        if (Objects.isNull(merchant)) {
            return "мерчант не указан, отключение не выполнялось";
        }
        try {
            Optional<MerchantConfigResponseDTO> config = merchantConfigService.findAll(clientId).stream()
                    .filter(merchantConfig -> merchant.equals(merchantConfig.merchant()))
                    .findFirst();
            if (config.isEmpty()) {
                return "настройка мерчанта у клиента не найдена";
            }
            merchantConfigService.update(config.get().id(), new MerchantConfigUpdateDTO(false, null, null, null));
            log.info("Мерчант {} отключён клиенту {} из-за некорректных реквизитов.", merchant, clientId);
            return "мерчант отключён";
        } catch (Exception e) {
            log.error("Не удалось отключить мерчанта {} клиенту {}: {}", merchant, clientId, e.getMessage(), e);
            return "отключить мерчанта не удалось (" + e.getMessage() + ")";
        }
    }
}
