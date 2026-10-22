package net.rcetech.domain.repository.incidents;

import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    boolean existsByTypeAndStatus(IncidentType type, IncidentStatus status);
}
