package net.rcetech.clients.service;

public interface SecurityApi {

    /**
     * Возвращает публичный ключ для валидации JWT-токенов.
     *
     * @return публичный ключ в формате строки
     */
    String getPublicKey();

}
