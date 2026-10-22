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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class MerchantCallbackService {

    private final MerchantCallbackRepository merchantCallbackRepository;

    public MerchantCallbackService(MerchantCallbackRepository merchantCallbackRepository) {
        this.merchantCallbackRepository = merchantCallbackRepository;
    }

    @Transactional
    public MerchantCallback save(MerchantCallbackEvent merchantCallbackEvent) {
        MerchantCallback merchantCallback = new MerchantCallback();
        merchantCallback.setCreatedAt(Instant.now());
        merchantCallback.setMerchant(merchantCallbackEvent.getMerchant());
        merchantCallback.setMerchantOrderId(merchantCallbackEvent.getMerchantOrderId());
        merchantCallback.setStatus(merchantCallbackEvent.getStatus());
        merchantCallback.setStatusDescription(merchantCallbackEvent.getStatusDescription());
        return merchantCallbackRepository.save(merchantCallback);
    }

    public Optional<MerchantCallback> findById(Long id) {
        return merchantCallbackRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<MerchantCallback> findAllUnprocessed(Order order) {
        List<String> merchantOrderIds = Stream.of(order.getId().toString(), order.getMerchantOrderId())
                .filter(Objects::nonNull)
                .toList();
        return merchantCallbackRepository.findAllByMerchantAndMerchantOrderIdInAndProcessedAtIsNullOrderByCreatedAtAscIdAsc(
                order.getMerchant(), merchantOrderIds);
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
