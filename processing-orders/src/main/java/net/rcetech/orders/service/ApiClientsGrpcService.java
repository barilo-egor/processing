package net.rcetech.orders.service;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tgb.cryptoexchange.grpc.generated.*;
import net.rcetech.orders.dto.ClientDTO;
import net.rcetech.orders.exceptions.BaseException;
import net.rcetech.orders.exceptions.UserNotFoundException;
import net.rcetech.orders.mapper.ClientMapper;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ApiClientsGrpcService {

    private final ClientMapper clientMapper;

    private final ClientsServiceGrpc.ClientsServiceFutureStub futureStub;

    public ApiClientsGrpcService(ClientsServiceGrpc.ClientsServiceFutureStub futureStub, ClientMapper clientMapper) {
        this.futureStub = futureStub;
        this.clientMapper = clientMapper;
    }

    /**
     * Асинхронно запрашивает данные клиента по его идентификатору через gRPC.
     *
     * @param clientId уникальный идентификатор клиента
     * @return {@link Mono}, содержащий {@link ClientDTO} в случае успешного выполнения
     * @throws UserNotFoundException если клиент с указанным ID не найден (gRPC NOT_FOUND)
     * @throws BaseException         в случае сбоя сети или других системных ошибок gRPC
     */
    public Mono<ClientDTO> getClientById(Long clientId) {
        log.debug("Реактивный gRPC запрос client: id {}", clientId);
        GetClientByIdGrpc request = GetClientByIdGrpc.newBuilder()
                .setId(clientId)
                .build();
        return Mono.fromFuture(() -> {
                    var guavaFuture = futureStub.getClientById(request);
                    var completableFuture = new CompletableFuture<GetClientByIdResponseGrpc>();

                    Futures.addCallback(
                            guavaFuture,
                            new FutureCallback<>() {
                                @Override
                                public void onSuccess(GetClientByIdResponseGrpc result) {
                                    completableFuture.complete(result);
                                }

                                @Override
                                public void onFailure(@NonNull Throwable t) {
                                    completableFuture.completeExceptionally(t);
                                }
                            },
                            MoreExecutors.directExecutor()
                    );
                    return completableFuture;
                })
                .map(clientMapper::clientByResponseToDTO)
                .onErrorResume(StatusRuntimeException.class, e -> {
                    if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                        log.warn("Клиент с ID {} не найден через gRPC", clientId);
                        return Mono.error(new UserNotFoundException());
                    }
                    log.error("gRPC ошибка для клиента ID {}: {}", clientId, e.getStatus());
                    return Mono.error(new BaseException("Ошибка gRPC: " + e.getStatus().getDescription()));
                })
                .onErrorMap(e -> !(e instanceof UserNotFoundException || e instanceof BaseException),
                        e -> new BaseException("Критическая ошибка gRPC: " + e.getMessage()));
    }

    /**
     * Асинхронно генерирует цифровую подпись для переданных данных клиента через gRPC.
     *
     * @param clientId уникальный идентификатор клиента
     * @param data     строка данных для подписания
     * @return {@link Mono}, содержащий сгенерированную строку подписи
     * @throws UserNotFoundException if клиент с указанным ID не найден (gRPC NOT_FOUND)
     * @throws BaseException         при сетевых сбоях, внутренних ошибках gRPC или критических исключениях
     */
    public Mono<String> createSignature(Long clientId, String data) {
        log.debug("Реактивный gRPC запрос client createSignature: id {} data {}", clientId, data);
        CreateSignatureGrpc request = CreateSignatureGrpc.newBuilder()
                .setClientId(clientId)
                .setData(data)
                .build();

        return Mono.fromFuture(() -> {
                    var guavaFuture = futureStub.createSignature(request);
                    var completableFuture = new CompletableFuture<CreateSignatureResponseGrpc>();

                    Futures.addCallback(
                            guavaFuture,
                            new FutureCallback<>() {
                                @Override
                                public void onSuccess(CreateSignatureResponseGrpc result) {
                                    completableFuture.complete(result);
                                }

                                @Override
                                public void onFailure(@NonNull Throwable t) {
                                    completableFuture.completeExceptionally(t);
                                }
                            },
                            MoreExecutors.directExecutor()
                    );
                    return completableFuture;
                })
                .map(CreateSignatureResponseGrpc::getSignature)
                .onErrorResume(StatusRuntimeException.class, e -> {
                    if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                        log.warn("Клиент с ID {} не найден через gRPC при создании подписи", clientId);
                        return Mono.error(new UserNotFoundException());
                    }
                    log.error("gRPC ошибка создания подписи для клиента ID {}: {}", clientId, e.getStatus());
                    return Mono.error(new BaseException("Ошибка gRPC: " + e.getStatus().getDescription()));
                })
                .onErrorMap(e -> !(e instanceof UserNotFoundException || e instanceof BaseException),
                        e -> new BaseException("Критическая ошибка gRPC при создании подписи: " + e.getMessage()));
    }

}
