package net.rcetech.clientsapi.service;

public interface SecurityApi {

    /**
     * Возвращает публичный ключ для валидации JWT-токенов.
     *
     * @return публичный ключ в формате строки
     */
    String getPublicKey();

}
