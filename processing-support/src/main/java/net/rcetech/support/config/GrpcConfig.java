package net.rcetech.support.config;

import io.grpc.Channel;
import net.rcetech.grpc.generated.MerchantConfigServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * Конфигурация gRPC-клиентов модуля support.
 */
@Configuration
public class GrpcConfig {

    /**
     * Stub для работы с конфигурациями мерчантов через микросервис api-merchant-details.
     *
     * @param channelFactory фабрика gRPC-каналов Spring
     * @return future stub сервиса конфигураций мерчантов
     */
    @Bean
    public MerchantConfigServiceGrpc.MerchantConfigServiceFutureStub merchantConfigServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel("api-merchant-details");
        return MerchantConfigServiceGrpc.newFutureStub(channel);
    }

}
