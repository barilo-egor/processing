package net.rcetech.api.config;

import io.grpc.Channel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import net.rcetech.grpc.generated.ClientsServiceGrpc;
import net.rcetech.grpc.generated.MerchantDetailsServiceGrpc;
import net.rcetech.grpc.generated.OrdersServiceGrpc;

@Configuration
public class GrpcConfig {

    @Bean
    public ClientsServiceGrpc.ClientsServiceFutureStub clientsServiceFutureStub(GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel("api-clients");
        return ClientsServiceGrpc.newFutureStub(channel);
    }

    @Bean
    public MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub merchantDetailsServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel("api-merchant-details");
        return MerchantDetailsServiceGrpc.newFutureStub(channel);
    }

    @Bean
    public OrdersServiceGrpc.OrdersServiceFutureStub ordersServiceFutureStub(
            GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel("api-orders");
        return OrdersServiceGrpc.newFutureStub(channel);
    }

}
