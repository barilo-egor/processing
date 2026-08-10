package net.rcetech.billing.mapper;

import com.google.protobuf.Timestamp;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import net.rcetech.grpc.generated.CreateTransactionGrpc;
import net.rcetech.grpc.generated.GetTransactionsGrpc;
import net.rcetech.grpc.generated.TransactionResponse;
import net.rcetech.billing.dto.TransactionDTO;
import net.rcetech.billing.entity.Transaction;
import net.rcetech.billing.enums.Operation;
import net.rcetech.billing.enums.TransactionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TransactionMapper {

    public TransactionDTO toDTO(CreateTransactionGrpc transactionGrpc) {
        return TransactionDTO.builder()
                .id(UUID.fromString(transactionGrpc.getId()))
                .clientId(transactionGrpc.getClientId())
                .amount(transactionGrpc.getAmount())
                .operation(Operation.valueOf(transactionGrpc.getOperation()))
                .type(TransactionType.valueOf(transactionGrpc.getType()))
                .comment(transactionGrpc.hasComment() ? transactionGrpc.getComment() : null)
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

    public Specification<Transaction> buildFindSpecification(GetTransactionsGrpc request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addIdentifiersPredicates(request, root, criteriaBuilder, predicates);
            addAmountPredicates(request, root, criteriaBuilder, predicates);
            addDatePredicates(request, root, criteriaBuilder, predicates);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addIdentifiersPredicates(GetTransactionsGrpc request, Root<Transaction> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (request.hasId()) {
            predicates.add(cb.equal(root.get("id"), UUID.fromString(request.getId())));
        }
        if (!CollectionUtils.isEmpty(request.getClientIdsList())) {
            predicates.add(root.get("clientId").in(request.getClientIdsList()));
        }
        if (!CollectionUtils.isEmpty(request.getOperationsList())) {
            List<Operation> operations = request.getOperationsList().stream().map(Operation::valueOf).toList();
            predicates.add(root.get("operation").in(operations));
        }
    }

    private void addAmountPredicates(GetTransactionsGrpc request, Root<Transaction> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (request.hasMinAmount()) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), request.getMinAmount()));
        }
        if (request.hasMaxAmount()) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amount"), request.getMaxAmount()));
        }
    }

    private void addDatePredicates(GetTransactionsGrpc request, Root<Transaction> root, CriteriaBuilder cb,
            List<Predicate> predicates) {
        if (request.hasCreatedAtFrom()) {
            Instant from = Instant.ofEpochSecond(request.getCreatedAtFrom().getSeconds(),
                    request.getCreatedAtFrom().getNanos());
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (request.hasCreatedAtTo()) {
            Instant to = Instant.ofEpochSecond(request.getCreatedAtTo().getSeconds(),
                    request.getCreatedAtTo().getNanos());
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
    }

    public TransactionResponse toTransactionResponse(TransactionDTO transactionDTO) {
        TransactionResponse.Builder builder = TransactionResponse.newBuilder()
                .setId(transactionDTO.getId().toString())
                .setClientId(transactionDTO.getClientId())
                .setOperation(transactionDTO.getOperation().name())
                .setType(transactionDTO.getType().name())
                .setAmount(transactionDTO.getAmount())
                .setCreatedAt(instantToTimestamp(transactionDTO.getCreatedAt()));
        if (StringUtils.isNotBlank(transactionDTO.getComment())) {
            builder.setComment(transactionDTO.getComment());
        }
        return builder.build();
    }

    private Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
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
