package net.rcetech.meta.orders;

public enum OrderStatusTransition {
    ALLOWED,
    SKIPPED,
    REJECTED;

    public static OrderStatusTransition of(OrderStatus from, OrderStatus to) {
        return switch (to) {
            case NEW -> SKIPPED;
            case SUCCESS -> switch (from) {
                case NEW, CANCELED, TIMEOUT, DISPUTE -> ALLOWED;
                case SUCCESS -> SKIPPED;
            };
            case CANCELED -> switch (from) {
                case NEW, DISPUTE -> ALLOWED;
                case CANCELED, TIMEOUT -> SKIPPED;
                case SUCCESS -> REJECTED;
            };
            case TIMEOUT -> switch (from) {
                case NEW -> ALLOWED;
                case SUCCESS, CANCELED, TIMEOUT, DISPUTE -> SKIPPED;
            };
            case DISPUTE -> switch (from) {
                case NEW, CANCELED, TIMEOUT -> ALLOWED;
                case DISPUTE -> SKIPPED;
                case SUCCESS -> REJECTED;
            };
        };
    }
}
