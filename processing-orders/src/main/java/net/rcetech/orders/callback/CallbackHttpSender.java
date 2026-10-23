package net.rcetech.orders.callback;

import net.rcetech.domain.model.orders.ClientCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CallbackHttpSender {

    private final RestClient callbackRestClient;

    public CallbackHttpSender(RestClient callbackRestClient) {
        this.callbackRestClient = callbackRestClient;
    }

    public void send(ClientCallback callback) {
        try {
            callbackRestClient.post()
                    .uri(callback.getUrl())
                    .header("Content-Type", "application/json")
                    .body(callback.getPayload())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new CallbackSendException(callback, e);
        }
    }
}
