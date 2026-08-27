package net.rcetech.domain.service.clients;

import lombok.NonNull;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    private final ClientMapper clientMapper;

    public ClientService(ClientRepository clientRepository, ClientMapper clientMapper) {
        this.clientRepository = clientRepository;
        this.clientMapper = clientMapper;
    }

    public Page<ClientResponseDTO> findAll(ClientFilter clientFilter, @NonNull Pageable pageable) {
        return clientRepository.findBy(ClientSpecifications.matches(clientFilter),
                query -> query.as(ClientResponseDTO.class).page(pageable));
    }

    public Optional<Client> findById(UUID id) {
        return clientRepository.findById(id);
    }

    public Client save(Client client) {
        return clientRepository.save(client);
    }

    public Client update(UUID id, UpdateClientDTO updateClientDTO) {
        Client client = findById(id).orElseThrow(
                () -> new BadRequestException("Client with id " + id + " not found.")
        );
        clientMapper.updateNotNull(updateClientDTO, client);
        return clientRepository.save(client);
    }
}
