package net.rcetech.clients.service;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.constants.Metrics;
import net.rcetech.clients.exceptions.ClientAlreadyExistsException;
import net.rcetech.clients.exceptions.InvalidApiKeyException;
import net.rcetech.clients.exceptions.PasswordValidationException;
import net.rcetech.clients.exceptions.UserNotFoundException;
import net.rcetech.domain.mapper.client.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.clients.dto.ClientDTO;
import net.rcetech.meta.clients.dto.GeneratedKeys;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class ClientProcessService {

    private static final String STRENGTH_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private final ClientService clientService;

    private final ClientCredentialsService clientCredentialsService;

    private final ClientMapper clientMapper;

    public ClientProcessService(ClientService clientService, ClientCredentialsService clientCredentialsService,
                                ClientMapper clientMapper) {
        this.clientService = clientService;
        this.clientCredentialsService = clientCredentialsService;
        this.clientMapper = clientMapper;
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
        if (clientService.existsByUsername(clientDTO.getUsername())) {
            throw new ClientAlreadyExistsException();
        }
        final String encryptedPassword = validateAndHashPassword(clientDTO.getPassword());
        Client client = Client.builder()
                .username(clientDTO.getUsername())
                .password(encryptedPassword)
                .build();
        GeneratedKeys generatedKeys = clientCredentialsService.generateApiSecret(client);
        client.setStatus(ClientStatus.ACTIVE);
        client = clientService.save(client);
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

        Client client = clientService.findByApiKey(hashedApiKey)
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
        Client client = clientService.findByUsername(username)
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
        Client client = clientService.findById(id)
                .orElseThrow(UserNotFoundException::new);
        return clientMapper.clientToDTO(client);
    }

    private String validateAndHashPassword(String password) {
        if (password == null || !password.matches(STRENGTH_REGEX)) {
            throw new PasswordValidationException();
        }
        return password;
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
