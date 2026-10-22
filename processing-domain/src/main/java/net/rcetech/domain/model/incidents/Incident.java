package net.rcetech.domain.model.incidents;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.domain.model.support.SupportUser;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "incident")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Long errorTimestamp;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private IncidentType type;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private SupportUser resolvedBy;

    private Instant resolvedAt;

    @Column(length = 1000)
    private String comment;
}
