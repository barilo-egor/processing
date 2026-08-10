package net.rcetech.clients.service;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.exceptions.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.rcetech.clients.constants.Metrics;
import net.rcetech.clients.dto.ClientDTO;
import net.rcetech.clients.dto.GeneratedKeys;
import net.rcetech.clients.entity.Client;
import net.rcetech.clients.enums.ClientStatus;
import tgb.cryptoexchange.clientsapi.exceptions.*;
import net.rcetech.clients.mapper.ClientMapper;
import net.rcetech.clients.repository.ClientRepository;

@Service
@Slf4j
@Transactional
public class ClientService {

    private static final String STRENGTH_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private final PasswordEncoder passwordEncoder;

    private final ClientRepository clientRepository;

    private final ClientCredentialsService clientCredentialsService;

    private final ClientMapper clientMapper;

    public ClientService(ClientRepository clientRepository, ClientCredentialsService clientCredentialsService,
            ClientMapper clientMapper, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.clientCredentialsService = clientCredentialsService;
        this.clientMapper = clientMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Создает нового клиента.
     * Метод хэширует пароль, сохраняет сущность в базу данных, а также
     * генерирует секретные ключи {@link GeneratedKeys} через {@link ClientCredentialsService}.
     *
     * @param clientDTO данные для создания нового клиента
     * @return {@link ClientDTO} созданного клиента
     * @throws ClientAlreadyExistsException если клиент с таким username уже зарегистрирован
     * @throws PasswordValidationException  если пароль не прошел валидацию
     */
    @Timed(value = Metrics.CLIENT_CREATE, description = "Метрики запросов на создание client.")
    public ClientDTO create(ClientDTO clientDTO) {
        log.debug("Запрос на создание client: username {}", clientDTO.getUsername());
        if (clientRepository.existsByUsername(clientDTO.getUsername())) {
            throw new ClientAlreadyExistsException();
        }
        final String encryptedPassword = validateAndHashPassword(clientDTO.getPassword());
        Client client = Client.builder().username(clientDTO.getUsername()).password(encryptedPassword).build();
        GeneratedKeys generatedKeys = clientCredentialsService.generateApiSecret(client);
        client.setStatus(ClientStatus.ACTIVE);
        client = clientRepository.save(client);
        log.debug("Создан клиент client: {}", clientDTO);
        return clientMapper.createdClientToDTO(client, generatedKeys);
    }

    /**
     * Возвращает данные клиента по его API-ключу с расшифровкой secret.
     * Метод хэширует входящий API-ключ по алгоритму SHA-256 для поиска в БД,
     * а затем расшифровывает защищенный секрет клиента с помощью AES-GCM.
     *
     * @param apiKey открытый API-ключ клиента
     * @return {@link ClientDTO} с данными клиента
     * @throws InvalidApiKeyException если передан пустой ключ или произошла ошибка его хэширования
     * @throws UserNotFoundException  если клиент с хэшем данного ключа не найден в системе
     */
    @Timed(value = Metrics.CLIENT_GET_BY_API_KEY, description = "Метрики запросов на получение client по apiKey.")
    public ClientDTO getClientByApiKey(String apiKey) {
        log.debug("Запрос client: apiKey {}", apiKey);
        String hashedApiKey;
        try {
            if (apiKey == null || apiKey.isBlank()) {
                throw new InvalidApiKeyException();
            }
            hashedApiKey = clientCredentialsService.hashSha256(apiKey);
        } catch (BaseException e) {
            throw new InvalidApiKeyException();
        }

        Client client = clientRepository.findByApiKey(hashedApiKey)
                .orElseThrow(UserNotFoundException::new);
        log.debug("Найден client: id {}, apiKey {}", client.getId(), client.getApiKey());
        String decryptedSecret = clientCredentialsService.decryptAesGcm(client.getSecret());
        return clientMapper.getClientByApiKeyDTO(client, decryptedSecret);
    }

    /**
     * Возвращает данные клиента по его username.
     *
     * @param username имя пользователя для поиска
     * @return {@link ClientDTO} с данными найденного клиента
     * @throws NotFoundException если клиент с указанным username не найден в системе
     */
    public ClientDTO getClientByUsername(String username) {
        log.debug("Запрос client: username {}", username);
        Client client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(username));
        return clientMapper.clientToDTO(client);
    }

    /**
     * Возвращает данные клиента по его ID.
     *
     * @param id уникальный идентификатор клиента
     * @return {@link ClientDTO} с данными найденного клиента
     * @throws UserNotFoundException если клиент с указанным ID не найден в системе
     */
    public ClientDTO getClientById(Long id) {
        log.debug("Запрос client: id {}", id);
        Client client = clientRepository.findClientById(id)
                .orElseThrow(UserNotFoundException::new);
        return clientMapper.clientToDTO(client);
    }

    private String validateAndHashPassword(String password) {
        if (password == null || !password.matches(STRENGTH_REGEX)) {
            throw new PasswordValidationException();
        }
        return passwordEncoder.encode(password);
    }

    /**
     * Создает цифровую подпись для данных на основе секретного ключа клиента.
     *
     * @param clientId уникальный идентификатор клиента
     * @param data     строка данных для подписания
     * @return строковое представление подписи в формате HMAC-SHA256 (Hex)
     */
    public String createSignature(Long clientId, String data) {
        log.debug("Запрос подписи клиента: id {}, data {}", clientId, data);
        String secret = getClientById(clientId).getSecret();
        return clientCredentialsService.generateHmacSha256(data, secret);
    }

}
