package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.orders.RequestMethod;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class RandomDetailsGenerator {

    private static final List<String> BANKS = List.of("Т-Банк", "Сбербанк", "Альфа-Банк", "ВТБ");

    private static final String TEST_ORDER_STATUS = "TEST";

    public ApiDetailsResponse generate(CreateOrderRequest createOrderRequest) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<RequestMethod> methods = List.copyOf(createOrderRequest.methods());
        RequestMethod method = methods.get(random.nextInt(methods.size()));
        Merchant[] merchants = Merchant.values();
        return new ApiDetailsResponse(
                UUID.randomUUID().toString(),
                merchants[random.nextInt(merchants.length)],
                UUID.randomUUID().toString(),
                TEST_ORDER_STATUS,
                null,
                new ApiDetailsResponse.Details(
                        method,
                        RequestMethod.CARD.equals(method) ? randomCardNumber(random) : randomPhoneNumber(random),
                        BANKS.get(random.nextInt(BANKS.size()))
                )
        );
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
