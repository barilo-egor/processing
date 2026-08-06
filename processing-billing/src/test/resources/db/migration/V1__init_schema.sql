CREATE TABLE transactions
(
    id         BINARY(16) NOT NULL,
    client_id  BIGINT      NOT NULL,
    amount     INT         NOT NULL,
    operation  VARCHAR(50) NOT NULL,
    type       VARCHAR(50) NOT NULL,
    comment    VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
