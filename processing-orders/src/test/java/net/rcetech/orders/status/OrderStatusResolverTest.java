package net.rcetech.orders.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = OrderStatusResolverTest.TestConfig.class)
class OrderStatusResolverTest {

    @Configuration
    @ComponentScan(basePackages = "net.rcetech.orders.status")
    static class TestConfig {
    }

    @Autowired
    private List<OrderStatusResolver> resolvers;

    @Test
    @DisplayName("Для каждого мерчанта должен присуствовать бин резолвера статуса.")
    void shouldPresentAllMerchants() {
        List<Merchant> actual = new ArrayList<>();
        for (OrderStatusResolver resolver : resolvers) {
            actual.addAll(resolver.getMerchants());
        }
        assertThat(actual).containsExactlyInAnyOrder(Merchant.values());
    }
}