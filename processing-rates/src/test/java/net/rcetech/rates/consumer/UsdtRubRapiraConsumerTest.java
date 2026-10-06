package net.rcetech.rates.consumer;

import com.github.tomakehurst.wiremock.client.WireMock;
import net.rcetech.rates.RatePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.EnableWireMock;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = {UsdtRubRapiraConsumer.class, UsdtRubRapiraConsumerTest.Configuration.class})
@EnableWireMock
class UsdtRubRapiraConsumerTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public RestClient rapiraRestClient(@Value("${wiremock.server.baseUrl}") String wireMockUrl) {
            return RestClient.builder().baseUrl(wireMockUrl).build();
        }
    }

    @Autowired
    private UsdtRubRapiraConsumer usdtRubRapiraConsumer;

    @Test
    @DisplayName("Метод должен вернуть пару USDT_RUB")
    void getPair_shouldReturnUsdtRub() {
        assertEquals(RatePair.USDT_RUB, usdtRubRapiraConsumer.getPair());
    }

    @ParameterizedTest
    @ValueSource(strings = {"87.92", "78.12"})
    @DisplayName("Метод должен вернуть курс из ответа рапиры.")
    void getPair_shouldReturnPair(String rate) {
        stubFor(get("/open/market/rates").willReturn(
                WireMock.status(200)
                        .withBody("""
                                {
                                    "data": [
                                        {
                                            "symbol": "USDT/RUB",
                                            "open": 94.58,
                                            "high": 94.8,
                                            "low": 94.56,
                                            "close": 94.76,
                                            "chg": 0.00190355,
                                            "change": 0.18,
                                            "fee": 0.0015,
                                            "lastDayClose": 94.76,
                                            "usdRate": 1.2831318936,
                                            "baseUsdRate": 0.01354086,
                                            "askPrice": %s,
                                            "bidPrice": 94.6,
                                            "baseCoinScale": 2,
                                            "coinScale": 2,
                                            "quoteCurrencyName": "Tether",
                                            "baseCurrency": "RUB",
                                            "quoteCurrency": "USDT"
                                        },
                                        {
                                            "symbol": "BTC/USDT",
                                            "open": 60676.2,
                                            "high": 60841,
                                            "low": 59873.5,
                                            "close": 60253,
                                            "chg": -0.00706823,
                                            "change": -423.2,
                                            "fee": 0.002,
                                            "lastDayClose": 60675,
                                            "usdRate": 60253,
                                            "baseUsdRate": 1,
                                            "askPrice": 60250.5,
                                            "bidPrice": 60247.6,
                                            "baseCoinScale": 2,
                                            "coinScale": 8,
                                            "quoteCurrencyName": "Bitcoin",
                                            "baseCurrency": "USDT",
                                            "quoteCurrency": "BTC"
                                        },
                                        {
                                            "symbol": "ETH/USDT",
                                            "open": 3014.22,
                                            "high": 3023.66,
                                            "low": 2978.16,
                                            "close": 3004,
                                            "chg": -0.00343164,
                                            "change": -10.22,
                                            "fee": 0.002,
                                            "lastDayClose": 3014.22,
                                            "usdRate": 3004,
                                            "baseUsdRate": 1,
                                            "askPrice": 3004.19,
                                            "bidPrice": 3004.18,
                                            "baseCoinScale": 2,
                                            "coinScale": 6,
                                            "quoteCurrencyName": "Ethereum",
                                            "baseCurrency": "USDT",
                                            "quoteCurrency": "ETH"
                                        }
                                    ]
                                }""".formatted(rate))
                        .withHeader("Content-Type", "application/json")));
        BigDecimal actual = usdtRubRapiraConsumer.consume();
        assertEquals(new BigDecimal(rate), actual);
    }
}