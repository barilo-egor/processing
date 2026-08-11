package net.rcetech.billing.mapper;

import net.rcetech.billing.dto.CreateTransactionRequest;
import net.rcetech.billing.dto.TransactionDTO;
import net.rcetech.billing.entity.Transaction;
import net.rcetech.billing.enums.Operation;
import net.rcetech.billing.enums.TransactionType;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionDTO toDTO(CreateTransactionRequest request) {
        if (request == null) {
            return null;
        }
        return TransactionDTO.builder()
                .id(request.id())
                .clientId(request.clientId())
                .amount(request.amount())
                .operation(Operation.valueOf(request.operation()))
                .type(TransactionType.valueOf(request.type()))
                .comment(request.comment())
                .build();
    }

    public Transaction toEntity(TransactionDTO transactionDTO) {
        return Transaction.builder()
                .id(transactionDTO.getId())
                .clientId(transactionDTO.getClientId())
                .amount(transactionDTO.getAmount())
                .operation(transactionDTO.getOperation())
                .type(transactionDTO.getType())
                .comment(transactionDTO.getComment())
                .build();
    }

    public TransactionDTO entityToDTO(Transaction transaction) {
        return TransactionDTO.builder()
                .id(transaction.getId())
                .clientId(transaction.getClientId())
                .operation(transaction.getOperation())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .comment(transaction.getComment())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

}
