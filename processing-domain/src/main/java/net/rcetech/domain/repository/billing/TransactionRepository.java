package net.rcetech.domain.repository.billing;

import net.rcetech.domain.model.billing.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Query("""
            select coalesce(sum(case when t.operation = net.rcetech.meta.billing.Operation.CREDIT
                                     then t.amount else -t.amount end), 0)
            from Transaction t where t.client.id = :clientId
            """)
    Integer calculateBalance(UUID clientId);

}
