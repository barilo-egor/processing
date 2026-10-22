package net.rcetech.rates.consumer;

import net.rcetech.rates.RateConsumeException;
import net.rcetech.rates.RatePair;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class UsdtRubRapiraConsumer implements RateConsumer {

    private final RestClient rapiraRestClient;

    public UsdtRubRapiraConsumer(RestClient rapiraRestClient) {
        this.rapiraRestClient = rapiraRestClient;
    }

    private record Response(List<Rate> data) {
        private record Rate(String symbol, BigDecimal askPrice) {}
    }

    @Override
    public BigDecimal consume() {
        Response response = rapiraRestClient.get()
                .uri("/open/market/rates")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Response.class);
        if (Objects.isNull(response) || Objects.isNull(response.data())) {
            throw new RateConsumeException("Тело ответа, либо data отсутствуют.");
        }
        return response.data().stream()
                .filter(r -> "USDT/RUB".equals(r.symbol()))
                .findFirst()
                .orElseThrow(() -> new RateConsumeException("Отсутствуют данные для пары USDT/RUB."))
                .askPrice();
    }

    @Override
    public RatePair getPair() {
        return RatePair.USDT_RUB;
    }
}
