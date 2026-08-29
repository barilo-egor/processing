package net.rcetech.domain.model.clients;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "api_key")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String preview;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String hash;

    @ManyToOne(fetch =  FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, name = "client_id")
    private Client client;
}
