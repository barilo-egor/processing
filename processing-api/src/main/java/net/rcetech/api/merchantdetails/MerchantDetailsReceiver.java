package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;

import java.util.UUID;

/**
 * Интерфейс получателя реквизитов в микросервисе merchant-details
 */
public interface MerchantDetailsReceiver {

    ApiDetailsResponse getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest);
}
