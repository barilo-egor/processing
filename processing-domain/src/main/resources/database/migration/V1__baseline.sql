create table client
(
    id                    binary(16)   not null,
    version               bigint       not null,
    username              varchar(255) not null,
    registered_at         timestamp(0) not null,
    callback_url          varchar(255),
    order_timeout_seconds integer      not null,
    commission_percent    decimal(3, 1),
    balance               bigint       not null default 0,
    comment               varchar(1000),
    min_withdrawal_amount bigint,
    primary key (id),
    constraint unique_username unique (username),
    constraint check_client_balance check (balance >= 0),
    constraint check_client_order_timeout_seconds check (order_timeout_seconds > 0),
    constraint check_client_commission_percent check (commission_percent >= 0 and commission_percent < 100),
    constraint check_client_min_withdrawal_amount check (min_withdrawal_amount > 0)
);

create table client_method
(
    client_id binary(16)  not null,
    method    varchar(30) not null,
    primary key (client_id, method),
    constraint fk_client_method foreign key (client_id) references client (id) on delete cascade
);

create table orders
(
    id                    binary(16)    not null,
    version               bigint        not null,
    created_at            timestamp(0)  not null,
    expires_at            timestamp(0),
    client_id             binary(16)    not null,
    internal_id           varchar(255)  not null,
    status                varchar(30)   not null,
    amount                integer       not null,
    merchant              varchar(30),
    merchant_order_id     varchar(255),
    merchant_order_status varchar(255),
    method                varchar(30),
    details               varchar(1000),
    bank                  varchar(255),
    callback_url          varchar(255),
    primary key (id),
    constraint unique_internal_id unique (internal_id),
    constraint unique_merchant_order_id unique (merchant, merchant_order_id),
    constraint fk_orders_client foreign key (client_id) references client (id),
    constraint check_orders_amount check (amount > 0),
    constraint check_orders_expires_at check (expires_at > created_at)
);

create table support_user
(
    id            binary(16)   not null,
    username      varchar(255) not null,
    registered_at timestamp(0) not null,
    primary key (id),
    constraint unique_username unique (username)
);

create table transaction
(
    id         binary(16)   not null,
    client_id  binary(16)   not null,
    created_at timestamp(0) not null,
    operation  varchar(30)  not null,
    amount     integer      not null,
    type       varchar(30)  not null,
    comment    varchar(255),
    primary key (id),
    constraint fk_transaction_client foreign key (client_id) references client (id),
    constraint check_transaction_amount check (amount >= 0)
);

create table withdrawal_request
(
    id                  binary(16)    not null,
    client_id           binary(16)    not null,
    status              varchar(30)   not null,
    created_at          timestamp(0)  not null,
    gross_source_amount int           not null,
    commission_percent  decimal(5, 2) not null,
    net_source_amount   int           not null,
    rate                decimal(5, 2) not null,
    target_amount       int           not null,
    address             varchar(100)  not null,
    primary key (id),
    constraint fk_withdrawal_request_client foreign key (client_id) references client (id),
    constraint check_withdrawal_request_gross_source_amount check (gross_source_amount > 0),
    constraint check_withdrawal_request_commission_percent check (commission_percent >= 0 and commission_percent < 100),
    constraint check_withdrawal_request_net_source_amount check (net_source_amount > 0),
    constraint check_withdrawal_request_net_not_above_gross check (net_source_amount <= gross_source_amount),
    constraint check_withdrawal_request_rate check (rate > 0),
    constraint check_withdrawal_request_target_amount check (target_amount > 0),
    constraint check_withdrawal_request_address check (char_length(trim(address)) > 0)
);

create table api_key
(
    id        bigint      not null auto_increment,
    preview   char(9)     not null,
    name      varchar(30) not null,
    hash      char(64)    not null,
    client_id binary(16)  not null,
    primary key (id),
    constraint unique_hash unique (hash),
    constraint unique_api_key_client_name unique (client_id, name),
    constraint fk_api_key_client foreign key (client_id) references client (id) on delete cascade
);

create table merchant_callback
(
    id                 bigint       not null auto_increment,
    created_at         timestamp(0) not null,
    order_id           binary(16)   not null,
    merchant           varchar(30)  not null,
    merchant_order_id  varchar(300),
    status             varchar(50)  not null,
    status_description varchar(50),
    primary key (id),
    constraint fk_merchant_callback_order foreign key (order_id) references orders (id)
);

create table incident
(
    id              bigint       not null auto_increment,
    created_at      timestamp(0) not null,
    error_timestamp bigint,
    status          varchar(30)  not null,
    type            varchar(100) not null,
    data            JSON         not null,
    resolved_by     binary(16),
    resolved_at     timestamp(0),
    comment         varchar(1000),
    primary key (id),
    constraint fk_incident_support_user foreign key (resolved_by) references support_user (id),
    constraint check_incident_resolution check (
        (status = 'NEW' and resolved_by is null and resolved_at is null)
            or (status = 'RESOLVED' and resolved_by is not null and resolved_at is not null)
        ),
    constraint check_incident_resolved_at check (resolved_at >= created_at)
);

create table SPRING_SESSION
(
    PRIMARY_ID            char(36)     not null,
    SESSION_ID            char(36)     not null,
    CREATION_TIME         bigint       not null,
    LAST_ACCESS_TIME      bigint       not null,
    MAX_INACTIVE_INTERVAL int          not null,
    EXPIRY_TIME           bigint       not null,
    PRINCIPAL_NAME        varchar(100),
    constraint SPRING_SESSION_PK primary key (PRIMARY_ID)
) row_format = dynamic;

create unique index SPRING_SESSION_IX1 on SPRING_SESSION (SESSION_ID);
create index SPRING_SESSION_IX2 on SPRING_SESSION (EXPIRY_TIME);
create index SPRING_SESSION_IX3 on SPRING_SESSION (PRINCIPAL_NAME);

create table SPRING_SESSION_ATTRIBUTES
(
    SESSION_PRIMARY_ID char(36)     not null,
    ATTRIBUTE_NAME     varchar(200) not null,
    ATTRIBUTE_BYTES    blob         not null,
    constraint SPRING_SESSION_ATTRIBUTES_PK primary key (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    constraint SPRING_SESSION_ATTRIBUTES_FK foreign key (SESSION_PRIMARY_ID) references SPRING_SESSION (PRIMARY_ID) on delete cascade
) row_format = dynamic;

create table oidc_session
(
    session_id          varchar(100) not null,
    issuer              varchar(255) not null,
    subject             varchar(255) not null,
    provider_session_id varchar(255),
    registered_at       timestamp(3) not null,
    information         blob         not null,
    primary key (session_id),
    index idx_oidc_session_provider_session (issuer, provider_session_id),
    index idx_oidc_session_subject (issuer, subject)
);
