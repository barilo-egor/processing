package net.rcetech.api.config;

import io.grpc.Channel;
import net.rcetech.grpc.generated.MerchantDetailsServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcConfig {

    @Bean
    public MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub merchantDetailsServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel("api-merchant-details");
        return MerchantDetailsServiceGrpc.newFutureStub(channel);
    }

}
