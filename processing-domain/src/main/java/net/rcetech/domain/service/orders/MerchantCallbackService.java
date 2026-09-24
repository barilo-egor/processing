package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MerchantCallbackService {

    private final MerchantCallbackRepository merchantCallbackRepository;

    public MerchantCallbackService(MerchantCallbackRepository merchantCallbackRepository) {
        this.merchantCallbackRepository = merchantCallbackRepository;
    }

    @Transactional
    public MerchantCallback save(Order order, MerchantCallbackEvent merchantCallbackEvent) {
        MerchantCallback merchantCallback = new MerchantCallback();
        merchantCallback.setOrder(order);
        merchantCallback.setCreatedAt(Instant.now());
        merchantCallback.setMerchant(merchantCallbackEvent.getMerchant());
        merchantCallback.setMerchantOrderId(merchantCallbackEvent.getMerchantOrderId());
        merchantCallback.setStatus(merchantCallbackEvent.getStatus());
        merchantCallback.setStatusDescription(merchantCallbackEvent.getStatusDescription());
        return merchantCallbackRepository.save(merchantCallback);
    }
}
