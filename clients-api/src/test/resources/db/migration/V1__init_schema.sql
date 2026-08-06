CREATE TABLE clients
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    username              VARCHAR(255) NOT NULL,
    password              VARCHAR(255) NOT NULL,
    api_key               VARCHAR(255) NOT NULL,
    api_key_preview       VARCHAR(255) NOT NULL,
    secret                VARCHAR(255) NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    callback_url          VARCHAR(255),
    order_timeout_seconds INT          NOT NULL DEFAULT 900,
    registered_at         DATETIME(6)  NOT NULL,

    CONSTRAINT uk_client_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE client_refresh_tokens
(
    token      BINARY(16) NOT NULL PRIMARY KEY,
    client_id  BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_client_id UNIQUE (client_id),
    CONSTRAINT fk_tokens_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE widthdrawal_request
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id  BIGINT       NOT NULL,
    amount     INT          NOT NULL,
    wallet     VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    comment    VARCHAR(255),
    created_at DATETIME(6)  NOT NULL,

    CONSTRAINT fk_withdrawal_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;