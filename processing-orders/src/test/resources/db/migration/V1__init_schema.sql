CREATE TABLE orders
(
    id                    BINARY(16)   NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    client_id             BIGINT       NOT NULL,
    internal_id           VARCHAR(255) NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    amount                INT          NOT NULL,
    merchant              VARCHAR(50)  NOT NULL,
    merchant_order_id     VARCHAR(255) NOT NULL,
    merchant_order_status VARCHAR(255) NOT NULL,
    enable_unique_amount  BOOLEAN      NOT NULL DEFAULT FALSE,
    callback_url          VARCHAR(1024),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_internal_id UNIQUE (internal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;