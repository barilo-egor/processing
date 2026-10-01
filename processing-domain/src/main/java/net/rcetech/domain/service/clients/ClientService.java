package net.rcetech.domain.service.clients;

import lombok.NonNull;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.Client_;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.clients.ClientSpecifications;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.ClientProjection;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ClientService {

    private static final String CLIENT_NOT_FOUND = "Client with id %s not found";

    private final ClientRepository clientRepository;

    private final ClientMapper clientMapper;

    public ClientService(ClientRepository clientRepository, ClientMapper clientMapper) {
        this.clientRepository = clientRepository;
        this.clientMapper = clientMapper;
    }

    public <T> Page<T> findAll(ClientFilter clientFilter, @NonNull Pageable pageable, Class<T> projectionType) {
        return clientRepository.findBy(ClientSpecifications.matches(clientFilter),
                query -> query.as(projectionType).page(pageable));
    }

    public Optional<Client> findById(UUID id) {
        return clientRepository.findById(id);
    }

    public <T> Optional<T> findById(UUID id, Class<T> projectionType) {
        return clientRepository.findBy(
                (from, builder) -> builder.equal(from.get(Client_.id), id),
                query -> query.as(projectionType).one()
        );
    }

    public Client save(Client client) {
        return clientRepository.save(client);
    }

    @Transactional
    public ClientProjection update(UUID id, UpdateClientDTO updateClientDTO) {
        return update(id, client -> clientMapper.updateNotNull(updateClientDTO, client));
    }

    @Transactional
    public ClientProjection update(UUID id, ClientUpdateRequest clientUpdateRequest) {
        return update(id, client -> clientMapper.updateNotNull(clientUpdateRequest, client));
    }

    private ClientProjection update(UUID id, Consumer<Client> clientMapFunction) {
        Client client = findById(id).orElseThrow(
                () -> new BadRequestException(String.format(CLIENT_NOT_FOUND, id))
        );
        clientMapFunction.accept(client);
        clientRepository.save(client);
        return findById(id, ClientProjection.class)
                .orElseThrow(() -> new BadRequestException(String.format(CLIENT_NOT_FOUND, id)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creditBalance(UUID clientId, Integer creditAmount) {
        Client client = findById(clientId)
                .orElseThrow(() -> new BadRequestException(String.format(CLIENT_NOT_FOUND, clientId.toString())));
        client.setBalance(client.getBalance() + creditAmount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void debitBalance(UUID clientId, Integer creditAmount) {
        Client client = findById(clientId)
                .orElseThrow(() -> new BadRequestException(String.format(CLIENT_NOT_FOUND, clientId.toString())));
        client.setBalance(client.getBalance() - creditAmount);
    }

    public void createIfNotExists(UUID id, String username) {
        if (clientRepository.existsById(id)) {
            return;
        }
        Client client = new Client();
        client.setId(id);
        client.setRegisteredAt(Instant.now());
        client.setUsername(username);
        clientRepository.save(client);
    }
}
