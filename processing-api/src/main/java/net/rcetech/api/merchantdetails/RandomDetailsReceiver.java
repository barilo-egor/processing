package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.orders.RequestMethod;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class RandomDetailsReceiver implements MerchantDetailsReceiver {

    private static final List<String> BANKS = List.of("Т-Банк", "Сбербанк", "Альфа-Банк", "ВТБ");

    public static final String TEST_ORDER_ID_PREFIX = "TEST_";

    private static final String TEST_ORDER_STATUS = "TEST";

    @Override
    public Optional<ApiDetailsResponse> getDetails(UUID clientId, UUID orderId, CreateOrderRequest clientOrderRequest,
                                                   Integer timeoutSeconds) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<RequestMethod> methods = List.copyOf(clientOrderRequest.methods());
        RequestMethod method = methods.get(random.nextInt(methods.size()));
        Merchant[] merchants = Merchant.values();
        return Optional.of(new ApiDetailsResponse(
                UUID.randomUUID().toString(),
                merchants[random.nextInt(merchants.length)],
                TEST_ORDER_ID_PREFIX + UUID.randomUUID(),
                TEST_ORDER_STATUS,
                null,
                new ApiDetailsResponse.Details(
                        method,
                        RequestMethod.CARD.equals(method) ? randomCardNumber(random) : randomPhoneNumber(random),
                        BANKS.get(random.nextInt(BANKS.size()))
                )
        ));
    }

    private String randomCardNumber(ThreadLocalRandom random) {
        return IntStream.range(0, 4)
                .mapToObj(i -> randomDigits(random, 4))
                .collect(Collectors.joining(" "));
    }

    private String randomPhoneNumber(ThreadLocalRandom random) {
        return "+79" + randomDigits(random, 9);
    }

    private String randomDigits(ThreadLocalRandom random, int count) {
        return random.ints(count, 0, 10)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());
    }
}
