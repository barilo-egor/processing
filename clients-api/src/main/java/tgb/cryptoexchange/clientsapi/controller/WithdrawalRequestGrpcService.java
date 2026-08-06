package tgb.cryptoexchange.clientsapi.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import tgb.cryptoexchange.clientsapi.dto.WithdrawalRequestDTO;
import tgb.cryptoexchange.clientsapi.mapper.WithdrawalMapper;
import tgb.cryptoexchange.clientsapi.service.WithdrawalRequestService;
import tgb.cryptoexchange.grpc.generated.CreateWithdrawalRequestGrpc;
import tgb.cryptoexchange.grpc.generated.UpdateWithdrawalRequestGrpc;
import tgb.cryptoexchange.grpc.generated.WithdrawalRequestServiceGrpc;

@GrpcService
@Slf4j
public class WithdrawalRequestGrpcService extends WithdrawalRequestServiceGrpc.WithdrawalRequestServiceImplBase {

    private final WithdrawalRequestService withdrawalRequestService;

    private final WithdrawalMapper mapper;

    public WithdrawalRequestGrpcService(WithdrawalRequestService withdrawalRequestService,
            WithdrawalMapper mapper) {
        this.withdrawalRequestService = withdrawalRequestService;
        this.mapper = mapper;
    }

    @Override
    public void createWithdrawalRequest(CreateWithdrawalRequestGrpc request, StreamObserver<Empty> responseObserver) {
        WithdrawalRequestDTO withdrawalRequestDTO = mapper.createWithdrawalRequestGrpcToDTO(request);
        withdrawalRequestService.saveWithdrawalRequest(withdrawalRequestDTO);

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateWithdrawalRequest(UpdateWithdrawalRequestGrpc request, StreamObserver<Empty> responseObserver) {
        WithdrawalRequestDTO withdrawalRequestDTO = mapper.updateWithdrawalRequestGrpcToDTO(request);
        withdrawalRequestService.updateWithdrawalRequest(request.hasId() ? request.getId().getValue() : null,
                withdrawalRequestDTO);

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

}
