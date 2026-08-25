create table client
(
    order_timeout_seconds integer      not null,
    registered_at         datetime(6) not null,
    id                    binary(16) not null,
    callback_url          varchar(255),
    username              varchar(255) not null,
    status                enum ('ACTIVE','BLOCKED') not null,
    primary key (id)
) engine=InnoDB;
create table orders
(
    amount                integer      not null,
    enable_unique_amount  bit          not null,
    client_id             bigint       not null,
    created_at            datetime(6) not null,
    id                    binary(16) not null,
    callback_url          varchar(255),
    internal_id           varchar(255) not null,
    merchant_order_id     varchar(255) not null,
    merchant_order_status varchar(255) not null,
    merchant              enum ('ALFA_TEAM','ALFA_TEAM_QR','ALFA_TEAM_WT','ASGARD','ASGARD_HIGH_CHECK','ASGARD_SIM','ASGARD_WT','BASE_51','BASE_51_HIGH_CHECK','BASE_51_LOW_CHECK','BASE_51_SIM','BIT_ZONE','BUCKS_PAY','BUCKS_PAY_SIM','CASH_OUT','CROCO_PAY','CUBE','CUBE_HIGH_CHECK','CUBE_LOW_CHECK','CUBE_SIM','DEORA','DEORA_LOW_CHECK','DEORA_PDF','DEORA_SIM','EVO_PAY','EXTASY_PAY','EXTASY_PAY_QR','EXTASY_PAY_RECEIPT','EXTASY_PAY_RECEIPT_3','FIAT_CUT','GAMBIT','GAMBIT_SIM','GEO_TRANSFER','GOAT_X','GOAT_X_SIM','HONEY_MONEY','LOTRIEN','LOTRIEN_PDF','MANY_PAY','MANY_PAY_HIGH_CHECK','MANY_PAY_LOW_CHECK','MERIDIAN_PAY','MERIDIAN_PAY_HIGH_CHECK','MERIDIAN_PAY_LOW_CHECK','MERIDIAN_PAY_NSPK','MERIDIAN_PAY_SIM','NOROS','NOROS_HIGH_CHECK','ONLY_PAYS','ONYX_PAY','PAYSCROW','PAYSCROW_HIGH_CHECK','PAYSCROW_SIM','PAYSCROW_WHITE_TRIANGLE','PAYSYNC','PAY_LEE','PAY_LEE_QR','PLATA_18','PLATA_PAYMENT','PRISMA_PAY','PW_PAY','ROSTRAST','RS_PAY','RS_PAY_BT','SETTLE_X','SETTLE_X_15','SOUZ','SOUZ_PDF','SOUZ_SBP_QR','SOUZ_SIM','STORM_TRADE','STORM_TRADE_13','YOLO','YOLO_SIM') not null,
    status                enum ('CANCELED','NEW','SUCCESS','TIMEOUT') not null,
    primary key (id)
) engine=InnoDB;
create table support_users
(
    id            bigint       not null auto_increment,
    registered_at datetime(6) not null,
    password      varchar(255) not null,
    username      varchar(255) not null,
    role          enum ('ADMINISTRATOR','NEW','OPERATOR') not null,
    primary key (id)
) engine=InnoDB;
create table transaction
(
    amount     integer not null,
    client_id  bigint  not null,
    created_at datetime(6) not null,
    id         binary(16) not null,
    comment    varchar(255),
    operation  enum ('CREDIT','DEBIT') not null,
    type       enum ('CLIENT_WITHDRAWAL','ORDER_CONFIRMATION') not null,
    primary key (id)
) engine=InnoDB;
create table user_refresh_tokens
(
    expires_at datetime(6) not null,
    user_id    bigint not null,
    token      binary(16) not null,
    primary key (token)
) engine=InnoDB;
create table withdrawal_request
(
    amount     integer      not null,
    client_id  bigint       not null,
    created_at datetime(6) not null,
    id         bigint       not null auto_increment,
    comment    varchar(255),
    wallet     varchar(255) not null,
    status     enum ('NEW') not null,
    primary key (id)
) engine=InnoDB;
alter table client
    add constraint UKah5c1ribskm746956okm9283n unique (username);
alter table orders
    add constraint UK7wx49wvedrow2xjb08sr1r7yb unique (internal_id);
alter table support_users
    add constraint UKnxwfk3jhoavko3t7hjrn32ohl unique (username);
alter table user_refresh_tokens
    add constraint UKtaaxjuy6au86lbi8yd3c6m89u unique (user_id);
