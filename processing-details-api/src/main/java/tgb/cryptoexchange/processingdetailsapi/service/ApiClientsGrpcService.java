package tgb.cryptoexchange.processingdetailsapi.service;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.grpc.generated.ClientsServiceGrpc;
import tgb.cryptoexchange.grpc.generated.GetClientByApiKeyGrpc;
import tgb.cryptoexchange.grpc.generated.GetClientByApiKeyResponseGrpc;
import tgb.cryptoexchange.processingdetailsapi.dto.ClientByApiKeyDTO;
import tgb.cryptoexchange.processingdetailsapi.enums.ClientStatus;
import tgb.cryptoexchange.processingdetailsapi.exceptions.BaseException;
import tgb.cryptoexchange.processingdetailsapi.exceptions.ClientNotFoundException;
import tgb.cryptoexchange.processingdetailsapi.exceptions.InvalidApiKeyException;

@Service
@Slf4j
public class ApiClientsGrpcService extends GrpcService {

    private final ClientsServiceGrpc.ClientsServiceFutureStub clientsFutureStub;

    public ApiClientsGrpcService(ClientsServiceGrpc.ClientsServiceFutureStub clientsFutureStub) {
        this.clientsFutureStub = clientsFutureStub;
    }

    /**
     * Получает данные клиента по хэшу API-ключа через gRPC.
     *
     * @param keyHash хэшированная строка API-ключа.
     * @return {@link ClientByApiKeyDTO} с секретом и статусом клиента.
     * @throws ClientNotFoundException если ключ не найден в системе (gRPC NOT_FOUND).
     * @throws InvalidApiKeyException  если неверный формат ключа (gRPC INVALID_ARGUMENT).
     * @throws BaseException           при сетевых сбоях и прочих системных ошибках gRPC.
     */
    public ClientByApiKeyDTO getClientByApiKey(String keyHash) {
        GetClientByApiKeyGrpc request = GetClientByApiKeyGrpc.newBuilder()
                .setApiKey(keyHash)
                .build();

        ListenableFuture<GetClientByApiKeyResponseGrpc> grpcFuture = clientsFutureStub.getClientByApiKey(request);
        try {
            GetClientByApiKeyResponseGrpc response = toCompletableFuture(grpcFuture).join();
            return ClientByApiKeyDTO.builder()
                    .username(response.getUsername())
                    .secret(response.getSecret())
                    .status(ClientStatus.valueOf(response.getStatus())).build();
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof StatusRuntimeException statusEx) {
                Status.Code code = statusEx.getStatus().getCode();
                if (code == Status.Code.NOT_FOUND) {
                    log.warn("Client not found via gRPC for hash: {}", keyHash);
                    throw new ClientNotFoundException();
                }
                if (code == Status.Code.INVALID_ARGUMENT) {
                    log.warn("Invalid key format sent to gRPC service: {}", statusEx.getMessage());
                    throw new InvalidApiKeyException();
                }
            }
            log.error("Error executing gRPC call", ex);
            throw new BaseException("Error executing gRPC call");
        }
    }

}
