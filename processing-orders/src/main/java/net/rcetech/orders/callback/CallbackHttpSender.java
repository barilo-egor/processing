package net.rcetech.orders.callback;

import net.rcetech.domain.model.orders.ClientCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CallbackHttpSender {

    private final RestClient callbackRestClient;

    private final CallbackTargetGuard callbackTargetGuard;

    public CallbackHttpSender(RestClient callbackRestClient, CallbackTargetGuard callbackTargetGuard) {
        this.callbackRestClient = callbackRestClient;
        this.callbackTargetGuard = callbackTargetGuard;
    }

    public void send(ClientCallback callback) {
        try {
            callbackTargetGuard.check(callback.getUrl());
            callbackRestClient.post()
                    .uri(callback.getUrl())
                    .header("Content-Type", "application/json")
                    .body(callback.getPayload())
                    .retrieve()
                    .toBodilessEntity();
        } catch (ForbiddenCallbackUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new CallbackSendException(callback, e);
        }
    }
}
