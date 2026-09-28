package net.rcetech.orders.callback;

import net.rcetech.meta.orders.dto.ClientOrderSummary;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CallbackHttpSender {

    private final ObjectMapper callbackMapper;

    private final RestClient callbackRestClient;

    public CallbackHttpSender(ObjectMapper callbackMapper, RestClient callbackRestClient) {
        this.callbackMapper = callbackMapper;
        this.callbackRestClient = callbackRestClient;
    }

    @Retryable(
            value = RestClientResponseException.class,
            multiplierString = "${app.retry.callback.multiplier:3.0}",
            delayString = "${app.retry.callback.delay:5000}",
            maxDelayString = "${app.retry.callback.maxDelay:120000}",
            maxRetriesString = "${app.retry.callback.maxRetries:2}"
    )
    public void send(ClientOrderSummary order) {
        callbackRestClient.post()
                .uri(order.callbackUrl())
                .header("Content-Type", "application/json")
                .body(callbackMapper.writeValueAsString(order))
                .retrieve()
                .toBodilessEntity();
    }
}
