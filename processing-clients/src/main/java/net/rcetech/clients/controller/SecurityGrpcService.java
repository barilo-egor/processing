package net.rcetech.clients.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.util.FileCopyUtils;
import net.rcetech.clients.exceptions.BaseException;
import tgb.cryptoexchange.grpc.generated.GetPublicJWTKeyResponseGrpc;
import tgb.cryptoexchange.grpc.generated.SecurityServiceGrpc;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

@GrpcService
@Slf4j
public class SecurityGrpcService extends SecurityServiceGrpc.SecurityServiceImplBase {

    private final Resource jwtPublicKeyResource;

    private String jwtPublicKeyString;

    public SecurityGrpcService(@Value("${secrets.jwt.public}") Resource jwtPublicKeyResource) {
        this.jwtPublicKeyResource = jwtPublicKeyResource;
    }

    @PostConstruct
    public void init() {
        try (Reader reader = new InputStreamReader(jwtPublicKeyResource.getInputStream(), StandardCharsets.UTF_8)) {
            this.jwtPublicKeyString = FileCopyUtils.copyToString(reader);
            log.info("JWT Public key successfully loaded from file.");
        } catch (Exception e) {
            log.error("Failed to read JWT public key file", e);
            throw new BaseException("Critical error: public key file is unreadable");
        }
    }

    @Override
    public void getPublicKey(Empty request, StreamObserver<GetPublicJWTKeyResponseGrpc> responseObserver) {
        responseObserver.onNext(GetPublicJWTKeyResponseGrpc.newBuilder().setJwtKey(jwtPublicKeyString).build());
        responseObserver.onCompleted();
    }

}
