package net.rcetech.meta.orders;

public interface MerchantStatusRecognizer {

    /**
     * Проверяет, является ли статус успешным.
     */
    boolean isSuccess(String status);

    /**
     * Проверяет, является ли статус неуспешным.
     */
    boolean isFail(String status);
}
