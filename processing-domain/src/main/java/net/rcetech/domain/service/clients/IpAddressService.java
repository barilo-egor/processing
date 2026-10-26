package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.clients.IpAddress;
import net.rcetech.domain.repository.clients.IpAddressRepository;
import net.rcetech.meta.clients.dto.IpAddressResponseDTO;
import net.rcetech.meta.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class IpAddressService {

    public static final int MAX_IP_ADDRESSES_PER_CLIENT = 10;

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

    private final IpAddressRepository ipAddressRepository;

    public IpAddressService(IpAddressRepository ipAddressRepository) {
        this.ipAddressRepository = ipAddressRepository;
    }

    public IpAddressResponseDTO create(String address, Client client) {
        String normalized = normalize(address);
        if (ipAddressRepository.existsByClientIdAndAddress(client.getId(), normalized)) {
            throw new BadRequestException("IP-адрес " + normalized + " уже добавлен.");
        }
        if (ipAddressRepository.countByClientId(client.getId()) >= MAX_IP_ADDRESSES_PER_CLIENT) {
            throw new BadRequestException("Достигнут лимит в " + MAX_IP_ADDRESSES_PER_CLIENT
                    + " IP-адресов. Удалите ненужный, чтобы добавить новый.");
        }
        IpAddress ipAddress = new IpAddress();
        ipAddress.setAddress(normalized);
        ipAddress.setClient(client);
        ipAddressRepository.save(ipAddress);
        return new IpAddressResponseDTO(ipAddress.getId(), ipAddress.getAddress());
    }

    private String normalize(String address) {
        String trimmed = address.trim();
        if (!IPV4_PATTERN.matcher(trimmed).matches() && !IPV6_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException("Некорректный IP-адрес " + trimmed + ".");
        }
        try {
            return InetAddress.getByName(trimmed).getHostAddress();
        } catch (UnknownHostException e) {
            throw new BadRequestException("Некорректный IP-адрес " + trimmed + ".");
        }
    }

    public List<IpAddressResponseDTO> findAllByClientId(Client client) {
        return ipAddressRepository.findAllByClientIdOrderById(client.getId());
    }

    public void delete(Client client, Long id) {
        if (!ipAddressRepository.existsByIdAndClientId(id, client.getId())) {
            throw new BadRequestException("Запись с идентификатором " + id + " не найдена.");
        }
        ipAddressRepository.deleteById(id);
    }
}
