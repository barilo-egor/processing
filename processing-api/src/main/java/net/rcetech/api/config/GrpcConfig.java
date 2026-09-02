package net.rcetech.api.config;

import io.grpc.Channel;
import net.rcetech.grpc.generated.MerchantConfigServiceGrpc;
import net.rcetech.grpc.generated.MerchantDetailsServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * Конфигурация gRPC-клиентов микросервиса merchant-details.
 */
@Configuration
public class GrpcConfig {

    private static final String API_MERCHANT_DETAILS_CHANNEL = "api-merchant-details";

    /**
     * Stub для получения реквизитов мерчанта.
     */
    @Bean
    public MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub merchantDetailsServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(API_MERCHANT_DETAILS_CHANNEL);
        return MerchantDetailsServiceGrpc.newFutureStub(channel);
    }

    /**
     * Stub для работы с конфигурациями мерчантов.
     */
    @Bean
    public MerchantConfigServiceGrpc.MerchantConfigServiceFutureStub merchantConfigServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(API_MERCHANT_DETAILS_CHANNEL);
        return MerchantConfigServiceGrpc.newFutureStub(channel);
    }

}
