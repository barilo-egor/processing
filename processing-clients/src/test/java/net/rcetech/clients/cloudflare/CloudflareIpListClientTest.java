package net.rcetech.clients.cloudflare;

import net.rcetech.meta.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClientException;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.wiremock.spring.EnableWireMock;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CloudflareIpListClient.class, properties = {
        "cloudflare.ip-list.enabled=true",
        "cloudflare.ip-list.base-url=${wiremock.server.baseUrl}",
        "cloudflare.ip-list.api-token=test-token",
        "cloudflare.ip-list.account-id=account",
        "cloudflare.ip-list.list-id=list",
        "cloudflare.ip-list.operation-poll-interval-millis=1",
        "cloudflare.ip-list.operation-poll-attempts=3"
})
@EnableWireMock
class CloudflareIpListClientTest {

    private static final String ITEMS_PATH = "/accounts/account/rules/lists/list/items";

    private static final String OPERATION_PATH = "/accounts/account/rules/lists/bulk_operations/";

    @Autowired
    private CloudflareIpListClient client;

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, op-1",
            "2001:db8:0:0:0:0:0:1, op-2"
    })
    @DisplayName("Метод должен добавить адрес в список и дождаться завершения операции.")
    void add_shouldAppendItemAndAwaitOperation(String address, String operationId) {
        stubFor(post(urlEqualTo(ITEMS_PATH)).willReturn(okJson(operation(operationId))));
        stubOperation(operationId, "pending", "running", "completed");

        client.add(address);

        verify(postRequestedFor(urlEqualTo(ITEMS_PATH))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(equalToJson("[{\"ip\":\"" + address + "\"}]")));
        verify(3, getRequestedFor(urlEqualTo(OPERATION_PATH + operationId)));
    }

    @ParameterizedTest
    @CsvSource({
            "item-1, op-1",
            "2c0b1a3e5f4d4e6f8a9b0c1d2e3f4a5b, op-2"
    })
    @DisplayName("Метод должен удалить запись по идентификатору и дождаться завершения операции.")
    void delete_shouldDeleteItemAndAwaitOperation(String itemId, String operationId) {
        stubFor(delete(urlEqualTo(ITEMS_PATH)).willReturn(okJson(operation(operationId))));
        stubOperation(operationId, "completed");

        client.delete(itemId);

        verify(deleteRequestedFor(urlEqualTo(ITEMS_PATH))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(equalToJson("{\"items\":[{\"id\":\"" + itemId + "\"}]}")));
        verify(1, getRequestedFor(urlEqualTo(OPERATION_PATH + operationId)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Item already exists", "Invalid IP"})
    @DisplayName("Метод должен выбросить исключение с текстом ошибки, если операция завершилась ошибкой.")
    void add_shouldThrowIfOperationFailed(String error) {
        stubFor(post(urlEqualTo(ITEMS_PATH)).willReturn(okJson(operation("op"))));
        stubFor(get(urlEqualTo(OPERATION_PATH + "op")).willReturn(okJson(
                "{\"success\":true,\"errors\":[],\"result\":{\"id\":\"op\",\"status\":\"failed\",\"error\":\""
                        + error + "\"}}")));

        BaseException exception = assertThrows(BaseException.class, () -> client.add("10.0.0.1"));

        assertEquals("Операция Cloudflare op завершилась ошибкой: " + error, exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "running"})
    @DisplayName("Метод должен выбросить исключение, если операция не завершилась за отведённое число проверок.")
    void delete_shouldThrowIfOperationNotFinished(String status) {
        stubFor(delete(urlEqualTo(ITEMS_PATH)).willReturn(okJson(operation("op"))));
        stubOperation("op", status);

        BaseException exception = assertThrows(BaseException.class, () -> client.delete("item"));

        assertAll(
                () -> assertEquals("Операция Cloudflare op не завершилась после 3 проверок статуса.",
                        exception.getMessage()),
                () -> verify(3, getRequestedFor(urlEqualTo(OPERATION_PATH + "op")))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    @DisplayName("Метод должен выбросить исключение, если Cloudflare отклонил запрос.")
    void add_shouldThrowIfRequestRejected(int status) {
        stubFor(post(urlEqualTo(ITEMS_PATH)).willReturn(status(status)));

        assertThrows(RestClientException.class, () -> client.add("10.0.0.1"));
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, 2001:db8::1",
            "192.168.0.1, 10.0.0.0/8"
    })
    @DisplayName("Метод должен вернуть записи со всех страниц списка.")
    void findAll_shouldReturnItemsFromAllPages(String firstIp, String secondIp) {
        stubFor(get(urlPathEqualTo(ITEMS_PATH)).withQueryParam("per_page", equalTo("500"))
                .withQueryParam("cursor", absent())
                .willReturn(okJson(items("a1", firstIp, "\"next\""))));
        stubFor(get(urlPathEqualTo(ITEMS_PATH)).withQueryParam("per_page", equalTo("500"))
                .withQueryParam("cursor", equalTo("next"))
                .willReturn(okJson(items("a2", secondIp, "null"))));

        List<CloudflareIpListClient.Item> items = client.findAll();

        assertEquals(List.of(new CloudflareIpListClient.Item("a1", firstIp),
                new CloudflareIpListClient.Item("a2", secondIp)), items);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10001", "10002"})
    @DisplayName("Метод должен выбросить исключение, если Cloudflare вернул неуспешный ответ.")
    void findAll_shouldThrowIfResponseNotSuccessful(String code) {
        stubFor(get(urlPathEqualTo(ITEMS_PATH)).willReturn(okJson(
                "{\"success\":false,\"errors\":[{\"code\":" + code + ",\"message\":\"error\"}],\"result\":null}")));

        BaseException exception = assertThrows(BaseException.class, () -> client.findAll());

        assertTrue(exception.getMessage().contains(code));
    }

    private void stubOperation(String operationId, String... statuses) {
        String scenario = "operation-" + operationId;
        for (int i = 0; i < statuses.length; i++) {
            String state = i == 0 ? Scenario.STARTED : "s" + i;
            stubFor(get(urlEqualTo(OPERATION_PATH + operationId))
                    .inScenario(scenario)
                    .whenScenarioStateIs(state)
                    .willSetStateTo(i + 1 < statuses.length ? "s" + (i + 1) : state)
                    .willReturn(okJson("{\"success\":true,\"errors\":[],\"result\":{\"id\":\"" + operationId
                            + "\",\"status\":\"" + statuses[i] + "\"}}")));
        }
    }

    private static String operation(String operationId) {
        return "{\"success\":true,\"errors\":[],\"result\":{\"operation_id\":\"" + operationId + "\"}}";
    }

    private static String items(String id, String ip, String after) {
        return "{\"success\":true,\"errors\":[],\"result\":[{\"id\":\"" + id + "\",\"ip\":\"" + ip
                + "\"}],\"result_info\":{\"cursors\":{\"after\":" + after + "}}}";
    }
}
