package net.rcetech.api.config;

import io.grpc.Channel;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.ApiMerchantConfigServiceGrpc;
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
    public ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceFutureStub merchantDetailsServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(API_MERCHANT_DETAILS_CHANNEL);
        return ApiDetailsRequestServiceGrpc.newFutureStub(channel);
    }

    /**
     * Stub для работы с конфигурациями мерчантов.
     */
    @Bean
    public ApiMerchantConfigServiceGrpc.ApiMerchantConfigServiceFutureStub merchantConfigServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(API_MERCHANT_DETAILS_CHANNEL);
        return ApiMerchantConfigServiceGrpc.newFutureStub(channel);
    }

}
