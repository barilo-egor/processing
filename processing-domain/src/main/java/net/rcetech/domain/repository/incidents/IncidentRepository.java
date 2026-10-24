package net.rcetech.domain.repository.incidents;

import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    boolean existsByTypeAndStatus(IncidentType type, IncidentStatus status);

    @Query(value = "select count(*) from incident where type = :type and status = :status "
            + "and data ->> '$.orderId' = :orderId", nativeQuery = true)
    long countByTypeAndStatusAndOrderId(String type, String status, String orderId);

    @Query(value = "select count(*) from incident where type = :type and status = :status "
            + "and data ->> '$.clientId' = :clientId", nativeQuery = true)
    long countByTypeAndStatusAndClientId(String type, String status, String clientId);
}
