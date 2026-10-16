package net.rcetech.api.merchantdetails;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.orders.OrderCanceledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class MerchantDetailsEventListener {

    private final ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    private final DetailsMapper detailsMapper;

    public MerchantDetailsEventListener(ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService,
                                        DetailsMapper detailsMapper) {
        this.apiMerchantDetailsGrpcService = apiMerchantDetailsGrpcService;
        this.detailsMapper = detailsMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void orderCanceled(OrderCanceledEvent event) {
        try {
            apiMerchantDetailsGrpcService.cancelOrder(detailsMapper.orderCanceledEventToDTO(event));
        } catch (Exception e) {
            log.error("Ошибка при отмене ордера {} мерчанта {} в merchant-details: {}",
                    event.merchantOrderId(), event.merchant(), e.getMessage(), e);
        }
    }
}
