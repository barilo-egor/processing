package net.rcetech.clients.cloudflare;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.config.ClientsExecutorConfig;
import net.rcetech.domain.service.clients.IpAddressService;
import net.rcetech.meta.clients.IpAddressesChangedEvent;
import net.rcetech.meta.incidents.CloudflareIpSyncErrorEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "cloudflare.ip-list.enabled", havingValue = "true")
public class CloudflareIpListSynchronizer {

    static final String ADD_ACTION = "добавить";

    static final String DELETE_ACTION = "удалить";

    private final CloudflareIpListClient cloudflareIpListClient;

    private final IpAddressService ipAddressService;

    private final ApplicationEventPublisher eventPublisher;

    public CloudflareIpListSynchronizer(CloudflareIpListClient cloudflareIpListClient,
                                        IpAddressService ipAddressService,
                                        ApplicationEventPublisher eventPublisher) {
        this.cloudflareIpListClient = cloudflareIpListClient;
        this.ipAddressService = ipAddressService;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async(ClientsExecutorConfig.CLOUDFLARE_EXECUTOR)
    public void ipAddressesChanged(IpAddressesChangedEvent event) {
        event.addresses().forEach(this::sync);
    }

    void sync(String address) {
        boolean used = ipAddressService.isUsed(address);
        try {
            Optional<String> itemId = findItemId(address);
            if (used && itemId.isEmpty()) {
                cloudflareIpListClient.add(address);
                log.info("IP-адрес {} добавлен в список Cloudflare.", address);
            } else if (!used && itemId.isPresent()) {
                cloudflareIpListClient.delete(itemId.get());
                log.info("IP-адрес {} удалён из списка Cloudflare.", address);
            }
        } catch (Exception e) {
            long timestamp = System.currentTimeMillis();
            String action = used ? ADD_ACTION : DELETE_ACTION;
            log.error("{} Не удалось {} IP-адрес {} в списке Cloudflare: {}", timestamp, action, address,
                    e.getMessage(), e);
            eventPublisher.publishEvent(new CloudflareIpSyncErrorEvent(address, action, e.getMessage(), timestamp));
        }
    }

    private Optional<String> findItemId(String address) {
        return cloudflareIpListClient.findAll().stream()
                .filter(item -> ipAddressService.toCanonical(withoutHostPrefix(item.ip()))
                        .filter(address::equals)
                        .isPresent())
                .map(CloudflareIpListClient.Item::id)
                .findFirst();
    }

    private String withoutHostPrefix(String ip) {
        if (ip.endsWith("/32") || ip.endsWith("/128")) {
            return ip.substring(0, ip.lastIndexOf('/'));
        }
        return ip;
    }
}
