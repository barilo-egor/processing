package net.rcetech.api.config;

import io.grpc.Channel;
import net.rcetech.api.interceptor.TestDetailsClientInterceptor;
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
    public ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub merchantDetailsServiceBlockingStub(
            GrpcChannelFactory channelFactory,
            TestDetailsClientInterceptor testDetailsClientInterceptor) {
        Channel channel = channelFactory.createChannel(API_MERCHANT_DETAILS_CHANNEL);
        return ApiDetailsRequestServiceGrpc.newBlockingStub(channel)
                .withInterceptors(testDetailsClientInterceptor);
    }

    /**
     * Stub для работы с конфигурациями мерчантов.
     */
    @Bean
    public ApiMerchantConfigServiceGrpc.ApiMerchantConfigServiceBlockingStub merchantConfigServiceBlockingStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(API_MERCHANT_DETAILS_CHANNEL);
        return ApiMerchantConfigServiceGrpc.newBlockingStub(channel);
    }

}
