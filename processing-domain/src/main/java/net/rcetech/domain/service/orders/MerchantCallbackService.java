package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.PredicateSpecification;
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

    @Transactional(readOnly = true)
    public <T> Page<T> findAll(PredicateSpecification<MerchantCallback> filter, Pageable pageable, Class<T> projectionType) {
        return merchantCallbackRepository.findBy(filter,
                query -> query.as(projectionType)
                        .page(PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                pageable.getSort()
                        )));
    }
}
