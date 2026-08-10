package net.rcetech.clients.service;

import lombok.RequiredArgsConstructor;
import net.rcetech.clients.dto.ClientDTO;
import net.rcetech.clients.mapper.ClientMapper;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateClientDTO;
import net.rcetech.meta.clients.dto.CreateClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateSignatureDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class ClientApiImpl implements ClientApi {

    private final ClientService clientService;

    private final ClientMapper mapper;

    @Override
    public CreateClientResponseDTO createClient(CreateClientDTO dto) {
        ClientDTO internalDto = mapper.toInternalDto(dto);
        ClientDTO createdClientDto = clientService.create(internalDto);
        return mapper.toCreateClientResponseDTO(createdClientDto);
    }

    @Override
    public ClientResponseDTO getClientByApiKey(String apiKey) {
        ClientDTO internalDto = clientService.getClientByApiKey(apiKey);
        return mapper.toClientResponseDTO(internalDto);
    }

    @Override
    public ClientResponseDTO getClientById(Long id) {
        ClientDTO internalDto = clientService.getClientById(id);
        return mapper.toClientResponseDTO(internalDto);
    }

    @Override
    public String createSignature(CreateSignatureDTO dto) {
        return clientService.createSignature(dto.clientId(), dto.data());
    }

}
