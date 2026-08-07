CREATE TABLE support_users
(
    id            BIGINT AUTO_INCREMENT,
    username      VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'NEW',
    registered_at DATETIME(6)  NOT NULL,

    CONSTRAINT pk_support_users PRIMARY KEY (id),
    CONSTRAINT uq_support_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_refresh_tokens
(
    token      BINARY(16)  NOT NULL,
    user_id    BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_user_refresh_tokens PRIMARY KEY (token),
    CONSTRAINT uq_user_refresh_tokens_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES support_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;