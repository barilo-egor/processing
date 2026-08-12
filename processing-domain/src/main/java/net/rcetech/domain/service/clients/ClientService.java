package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Optional<Client> findById(Long id) {
        return clientRepository.findById(id);
    }

    public  Optional<Client> findByUsername(String username) {
        return clientRepository.findByUsername(username);
    }

    public Optional<Client> findByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey);
    }

    public Client save(Client client) {
        return clientRepository.save(client);
    }

    public boolean existsByUsername(String username) {
        return clientRepository.existsByUsername(username);
    }
}
