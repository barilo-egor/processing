package net.rcetech.billing.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.grpc.server.service.GrpcService;
import net.rcetech.grpc.generated.*;
import net.rcetech.billing.dto.TransactionDTO;
import net.rcetech.billing.mapper.TransactionMapper;
import net.rcetech.billing.service.TransactionService;

@GrpcService
@Slf4j
public class TransactionGrpcService extends TransactionsServiceGrpc.TransactionsServiceImplBase {

    private final TransactionMapper transactionMapper;

    private final TransactionService transactionService;

    public TransactionGrpcService(TransactionMapper transactionMapper, TransactionService transactionService) {
        this.transactionMapper = transactionMapper;
        this.transactionService = transactionService;
    }

    @Override
    public void createTransaction(CreateTransactionGrpc request, StreamObserver<Empty> responseObserver) {
        TransactionDTO transactionDTO = transactionMapper.toDTO(request);
        transactionService.create(transactionDTO);

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTransactions(GetTransactionsGrpc request,
            StreamObserver<GetTransactionsResponseGrpc> responseObserver) {
        PaginationParams pagination = request.getPagination();

        Page<TransactionDTO> dtoPage = transactionService.findTransactions(
                transactionMapper.buildFindSpecification(request),
                pagination.getPage(),
                pagination.getSize(),
                pagination.getSortersList().stream().toList());

        GetTransactionsResponseGrpc response = GetTransactionsResponseGrpc.newBuilder()
                .addAllTransactions(
                        dtoPage.getContent().stream().map(transactionMapper::toTransactionResponse).toList())
                .setTotalElements(dtoPage.getTotalElements())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
