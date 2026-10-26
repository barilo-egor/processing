package net.rcetech.clients.cloudflare;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.rcetech.meta.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "cloudflare.ip-list.enabled", havingValue = "true")
public class CloudflareIpListClient {

    private static final String ITEMS_PATH = "/accounts/{accountId}/rules/lists/{listId}/items";

    private static final String OPERATION_PATH = "/accounts/{accountId}/rules/lists/bulk_operations/{operationId}";

    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;

    private final String accountId;

    private final String listId;

    private final Duration operationPollInterval;

    private final int operationPollAttempts;

    public CloudflareIpListClient(
            @Value("${cloudflare.ip-list.base-url:https://api.cloudflare.com/client/v4}") String baseUrl,
            @Value("${cloudflare.ip-list.api-token}") String apiToken,
            @Value("${cloudflare.ip-list.account-id}") String accountId,
            @Value("${cloudflare.ip-list.list-id}") String listId,
            @Value("${cloudflare.ip-list.operation-poll-interval-millis:1000}") long operationPollIntervalMillis,
            @Value("${cloudflare.ip-list.operation-poll-attempts:30}") int operationPollAttempts) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBearerAuth(apiToken))
                .build();
        this.accountId = accountId;
        this.listId = listId;
        this.operationPollInterval = Duration.ofMillis(operationPollIntervalMillis);
        this.operationPollAttempts = operationPollAttempts;
    }

    public List<Item> findAll() {
        List<Item> items = new ArrayList<>();
        String cursor = null;
        do {
            String pageCursor = cursor;
            Response<List<Item>> response = restClient.get()
                    .uri(builder -> {
                        builder.path(ITEMS_PATH).queryParam("per_page", PAGE_SIZE);
                        if (Objects.nonNull(pageCursor)) {
                            builder.queryParam("cursor", pageCursor);
                        }
                        return builder.build(accountId, listId);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Response<List<Item>> page = requireSuccess(response);
            items.addAll(page.result());
            cursor = Objects.isNull(page.resultInfo()) || Objects.isNull(page.resultInfo().cursors())
                    ? null
                    : page.resultInfo().cursors().after();
        } while (Objects.nonNull(cursor));
        return items;
    }

    public void add(String address) {
        Response<Operation> response = restClient.post()
                .uri(ITEMS_PATH, accountId, listId)
                .body(List.of(Map.of("ip", address)))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        awaitOperation(requireSuccess(response).result().operationId());
    }

    public void delete(String itemId) {
        Response<Operation> response = restClient.method(HttpMethod.DELETE)
                .uri(ITEMS_PATH, accountId, listId)
                .body(Map.of("items", List.of(Map.of("id", itemId))))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        awaitOperation(requireSuccess(response).result().operationId());
    }

    private void awaitOperation(String operationId) {
        for (int attempt = 0; attempt < operationPollAttempts; attempt++) {
            Response<OperationStatus> response = restClient.get()
                    .uri(OPERATION_PATH, accountId, operationId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            OperationStatus status = requireSuccess(response).result();
            if ("completed".equals(status.status())) {
                return;
            }
            if ("failed".equals(status.status())) {
                throw new BaseException("Операция Cloudflare " + operationId + " завершилась ошибкой: "
                        + status.error());
            }
            sleep();
        }
        throw new BaseException("Операция Cloudflare " + operationId + " не завершилась после "
                + operationPollAttempts + " проверок статуса.");
    }

    private void sleep() {
        try {
            Thread.sleep(operationPollInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("Ожидание операции Cloudflare прервано.", e);
        }
    }

    private <T> Response<T> requireSuccess(Response<T> response) {
        if (Objects.isNull(response) || !response.success() || Objects.isNull(response.result())) {
            throw new BaseException("Cloudflare вернул неуспешный ответ: "
                    + (Objects.isNull(response) ? null : response.errors()));
        }
        return response;
    }

    public record Item(String id, String ip) {}

    record Response<T>(boolean success, List<Map<String, Object>> errors, T result,
                       @JsonProperty("result_info") ResultInfo resultInfo) {}

    record ResultInfo(Cursors cursors) {}

    record Cursors(String after) {}

    record Operation(@JsonProperty("operation_id") String operationId) {}

    record OperationStatus(String status, String error) {}
}
