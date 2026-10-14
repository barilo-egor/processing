package net.rcetech.meta.incidents;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum IncidentType {
    BALANCE_UPDATE_ERROR(
            "После создания транзакции ${transactionId} не удалось обновить баланс клиента ${clientId}.",
            "Вручную запустить перерасчет баланса клиента, если причина ошибки устранена."
    ),
    TRANSACTION_CREATION_ERROR("Не удалось создать транзакцию после обновления статуса ордера ${orderId} " +
            "до ${orderStatus}.",
            "Проверить наличие КБ по ордеру от мерчанта(либо любое другое подтверждение поступления оплаты " +
                    "от клиента) и создать корректировочную транзакцию."),
    CLIENT_COMMISSION_PERCENT_NOT_SET("Комиссия клиента ${clientId} не установлена, " +
            "из-за чего клиент не может создать заявку на вывод.",
            "Установить клиенту комиссию."),
    CLIENT_MIN_WITHDRAWAL_AMOUNT_NOT_SET("Минимальная сумма вывода клиента ${clientId} не установлена, " +
            "из-за чего клиент не может создать заявку на вывод.",
            "Установить клиенту минимальную сумму вывода."),
    MERCHANT_CALLBACK_HANDLE_ERROR(
            "Ошибка обработки КБ от мерчанта ${merchant}: константа статуса - ${status}, описание статуса " +
                    "- ${statusDescription}, идентификатор ордера в системе мерчанта - ${merchantOrderId}",
            "Найти КБ мерчанта, определить итоговый статус ордера исходя из статуса мерчанта и провести, " +
                    "при необходимости, ордер по маршруту."),
    MERCHANT_CALLBACK_STATUS_NOT_RESOLVED("Для статуса ${orderStatus}(статус мерчанта ${merchantOrderStatus}) " +
            "ордера ${orderId} не определен маршрут.",
            "Провести ордер по маршруту вручную, сообщить разработчику.");

    private final String description;
    private final String guide;
}
