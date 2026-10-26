package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.IpAddress;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.clients.IpAddressRepository;
import net.rcetech.meta.clients.IpAddressesChangedEvent;
import net.rcetech.meta.clients.dto.IpAddressResponseDTO;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class IpAddressService {

    public static final int MAX_IP_ADDRESSES_PER_CLIENT = 10;

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

    private final IpAddressRepository ipAddressRepository;

    private final ClientRepository clientRepository;

    private final ApplicationEventPublisher eventPublisher;

    public IpAddressService(IpAddressRepository ipAddressRepository, ClientRepository clientRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.ipAddressRepository = ipAddressRepository;
        this.clientRepository = clientRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public IpAddressResponseDTO create(String address, Client client) {
        String normalized = normalize(address);
        Client lockedClient = clientRepository.findForUpdateById(client.getId())
                .orElseThrow(() -> new BadRequestException("Клиент не найден."));
        if (ipAddressRepository.existsByClientIdAndAddress(client.getId(), normalized)) {
            throw new BadRequestException("IP-адрес " + normalized + " уже добавлен.");
        }
        if (ipAddressRepository.countByClientId(client.getId()) >= MAX_IP_ADDRESSES_PER_CLIENT) {
            throw new BadRequestException("Достигнут лимит в " + MAX_IP_ADDRESSES_PER_CLIENT
                    + " IP-адресов. Удалите ненужный, чтобы добавить новый.");
        }
        IpAddress ipAddress = new IpAddress();
        ipAddress.setAddress(normalized);
        ipAddress.setClient(lockedClient);
        ipAddressRepository.save(ipAddress);
        eventPublisher.publishEvent(new IpAddressesChangedEvent(List.of(normalized)));
        return new IpAddressResponseDTO(ipAddress.getId(), ipAddress.getAddress());
    }

    public boolean isAllowed(Client client, String remoteAddress) {
        if (!client.isIpRestrictionEnabled()) {
            return true;
        }
        return Objects.nonNull(remoteAddress) && toCanonical(remoteAddress.trim())
                .map(address -> ipAddressRepository.existsByClientIdAndAddress(client.getId(), address))
                .orElse(false);
    }

    private String normalize(String address) {
        String trimmed = address.trim();
        return toCanonical(trimmed)
                .orElseThrow(() -> new BadRequestException("Некорректный IP-адрес " + trimmed + "."));
    }

    public boolean isUsed(String address) {
        return ipAddressRepository.existsByAddress(address);
    }

    public Optional<String> toCanonical(String address) {
        if (!IPV4_PATTERN.matcher(address).matches() && !IPV6_PATTERN.matcher(address).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(address).getHostAddress());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    public List<IpAddressResponseDTO> findAllByClientId(Client client) {
        return ipAddressRepository.findAllByClientIdOrderById(client.getId());
    }

    @Transactional
    public void delete(Client client, Long id) {
        IpAddress ipAddress = ipAddressRepository.findByIdAndClientId(id, client.getId())
                .orElseThrow(() -> new BadRequestException("Запись с идентификатором " + id + " не найдена."));
        ipAddressRepository.delete(ipAddress);
        eventPublisher.publishEvent(new IpAddressesChangedEvent(List.of(ipAddress.getAddress())));
    }
}
