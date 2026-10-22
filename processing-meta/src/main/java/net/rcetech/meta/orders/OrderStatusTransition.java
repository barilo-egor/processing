package net.rcetech.meta.orders;

public enum OrderStatusTransition {
    ALLOWED,
    SKIPPED,
    REJECTED;

    public static OrderStatusTransition of(OrderStatus from, OrderStatus to) {
        return switch (to) {
            case SEARCHING -> switch (from) {
                case SEARCHING -> SKIPPED;
                case DETAILS_NOT_FOUND, NEW, CANCELED, TIMEOUT, DISPUTE, SUCCESS -> REJECTED;
            };
            case DETAILS_NOT_FOUND, NEW -> switch (from) {
                case SEARCHING -> ALLOWED;
                case DETAILS_NOT_FOUND, NEW, CANCELED, TIMEOUT, DISPUTE, SUCCESS -> SKIPPED;
            };
            case SUCCESS -> switch (from) {
                case NEW, CANCELED, TIMEOUT, DISPUTE -> ALLOWED;
                case DETAILS_NOT_FOUND, SUCCESS -> SKIPPED;
                case SEARCHING -> REJECTED;
            };
            case CANCELED -> switch (from) {
                case SEARCHING, NEW, DISPUTE -> ALLOWED;
                case DETAILS_NOT_FOUND, CANCELED, TIMEOUT -> SKIPPED;
                case SUCCESS -> REJECTED;
            };
            case TIMEOUT -> switch (from) {
                case NEW -> ALLOWED;
                case DETAILS_NOT_FOUND, SUCCESS, CANCELED, TIMEOUT, DISPUTE -> SKIPPED;
                case SEARCHING -> REJECTED;
            };
            case DISPUTE -> switch (from) {
                case NEW, CANCELED, TIMEOUT -> ALLOWED;
                case DETAILS_NOT_FOUND, DISPUTE -> SKIPPED;
                case SEARCHING, SUCCESS -> REJECTED;
            };
        };
    }
}
