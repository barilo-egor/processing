package net.rcetech.orders.config;

import io.grpc.Channel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import tgb.cryptoexchange.grpc.generated.ClientsServiceGrpc;

@Configuration
public class GrpcClientConfig {

    @Bean
    public ClientsServiceGrpc.ClientsServiceFutureStub clientsServiceFutureStub(GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel("api-clients");
        return ClientsServiceGrpc.newFutureStub(channel);
    }

}
