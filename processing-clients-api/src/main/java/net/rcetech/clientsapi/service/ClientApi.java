package net.rcetech.clientsapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.rcetech.clientsapi.dto.ClientResponseDTO;
import net.rcetech.clientsapi.dto.CreateClientDTO;
import net.rcetech.clientsapi.dto.CreateClientResponseDTO;
import net.rcetech.clientsapi.dto.CreateSignatureDTO;

public interface ClientApi {

    /**
     * Создает нового клиента.
     *
     * @param dto данные для регистрации
     * @return созданный клиент с ключами доступа
     * @throws ClientAlreadyExistsException если клиент с таким username уже существует
     * @throws PasswordValidationException  если пароль не прошел валидацию
     * @throws FieldNotBeEmptyException     если переданы пустые обязательные поля
     */
    CreateClientResponseDTO createClient(CreateClientDTO createClientDTO);

    /**
     * Возвращает клиента по API-ключу.
     *
     * @param apiKey API-ключ клиента
     * @return данные о клиенте
     * @throws ClientNotFoundException если клиент не найден
     * @throws InvalidApiKeyException  если передан некорректный или пустой ключ
     */
    ClientResponseDTO getClientByApiKey(@NotBlank String apiKey);

    /**
     * Возвращает клиента по его идентификатору.
     *
     * @param id идентификатор клиента
     * @return данные о клиенте
     * @throws ClientNotFoundException если клиент не найден
     */
    ClientResponseDTO getClientById(@NotNull @Positive Long id);

    /**
     * Создает цифровую подпись для переданных данных.
     *
     * @param dto данные и идентификатор клиента для подписи
     * @return строка сформированной цифровой подписи
     * @throws ClientNotFoundException если клиент не найден
     */
    String createSignature(@Valid CreateSignatureDTO dto);

}
