package net.rcetech.clients.service;

import jakarta.validation.Valid;
import net.rcetech.meta.clients.dto.CreateWithdrawalRequestDTO;
import net.rcetech.meta.clients.dto.UpdateWithdrawalRequestDTO;

public interface WithdrawalRequestApi {

    /**
     * Создает заявку на вывод денежных средств.
     *
     * @param dto данные для создания заявки на вывод
     */
    void createWithdrawalRequest(@Valid CreateWithdrawalRequestDTO dto);

    /**
     * Обновляет существующую заявку на вывод.
     *
     * @param dto данные для обновления (id обязателен)
     * @throws FieldNotBeEmptyException           если не передан id заявки
     * @throws WithdrawalRequestNotFoundException если заявка с таким id не найдена
     */
    void updateWithdrawalRequest(@Valid UpdateWithdrawalRequestDTO dto);

}
