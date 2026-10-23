package net.rcetech.domain.repository.orders;

import net.rcetech.domain.model.orders.ClientCallback;
import net.rcetech.meta.orders.ClientCallbackStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ClientCallbackRepository extends JpaRepository<ClientCallback, Long> {

    @Query("""
            select c1 from ClientCallback c1
            where c1.status = net.rcetech.meta.orders.ClientCallbackStatus.PENDING
              and c1.nextAttemptAt <= :now
              and not exists (
                  select c2.id from ClientCallback c2
                  where c2.orderId = c1.orderId
                    and c2.status in (net.rcetech.meta.orders.ClientCallbackStatus.PENDING,
                                      net.rcetech.meta.orders.ClientCallbackStatus.SENDING)
                    and c2.id < c1.id
              )
            order by c1.id""")
    List<ClientCallback> findReadyToSend(Instant now, Limit limit);

    List<ClientCallback> findAllByStatusAndLastAttemptAtBefore(ClientCallbackStatus status, Instant lastAttemptAt);

    @Modifying
    @Query("delete from ClientCallback c where c.status in :statuses and c.createdAt < :createdBefore")
    int deleteAllByStatusInAndCreatedAtBefore(Collection<ClientCallbackStatus> statuses, Instant createdBefore);
}
