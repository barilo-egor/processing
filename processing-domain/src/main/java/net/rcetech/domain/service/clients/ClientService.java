package net.rcetech.domain.service.clients;

import lombok.NonNull;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.projection.ClientProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Page<ClientProjection> findAll(ClientFilter clientFilter, @NonNull Pageable pageable) {
        return clientRepository.findBy(ClientSpecifications.matches(clientFilter),
                query -> query.as(ClientProjection.class).page(pageable));
    }

    public Optional<Client> findById(Long id) {
        return clientRepository.findById(id);
    }

    public  Optional<Client> findByUsername(String username) {
        return clientRepository.findByUsername(username);
    }

    public Client save(Client client) {
        return clientRepository.save(client);
    }

    public boolean existsByUsername(String username) {
        return clientRepository.existsByUsername(username);
    }
}
