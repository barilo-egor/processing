create table client
(
    id                    binary(16)   not null,
    version               bigint       not null,
    username              varchar(255) not null,
    registered_at         timestamp(0) not null,
    status                varchar(30)  not null,
    callback_url          varchar(255),
    order_timeout_seconds integer      not null,
    commission_percent    decimal(3, 1),
    balance               bigint default 0 check (balance >= 0),
    primary key (id)
) engine = InnoDB;
alter table client
    add constraint unique_username unique (username);

create table orders
(
    id                    binary(16)    not null,
    version               bigint        not null,
    created_at            timestamp(0)  not null,
    expires_at            timestamp(0)  not null,
    client_id             binary(16)    not null,
    internal_id           varchar(255)  not null,
    status                varchar(30)   not null,
    amount                integer       not null,
    enable_unique_amount  bit           not null,
    merchant              varchar(30)   not null,
    merchant_order_id     varchar(255)  not null,
    merchant_order_status varchar(255)  not null,
    method                varchar(30)   not null,
    details               varchar(1000) not null,
    bank                  varchar(255)  not null,
    callback_url          varchar(255),
    primary key (id)
) engine = InnoDB;
alter table orders
    add constraint unique_internal_id unique (internal_id),
    add constraint unique_merchant_order_id unique (merchant_order_id),
    add constraint fk_orders_client foreign key (client_id) references client (id) on
        delete
        cascade on
        update cascade;

create table support_users
(
    id            bigint       not null auto_increment,
    username      varchar(255) not null,
    registered_at timestamp(0) not null,
    primary key (id)
) engine = InnoDB;
alter table support_users
    add constraint unique_username unique (username);

create table transaction
(
    id         bigint       not null auto_increment,
    client_id  binary(16)   not null,
    created_at timestamp(0) not null,
    operation  varchar(30)  not null,
    amount     integer      not null,
    type       varchar(30)  not null,
    comment    varchar(255),
    primary key (id)
) engine = InnoDB;
alter table transaction
    add constraint fk_transaction_client foreign key (client_id) references client (id) on delete cascade on update cascade;

create table withdrawal_request
(
    id                  binary(16)    not null,
    client_id           binary(16)    not null,
    status              varchar(30)   not null,
    created_at          timestamp(0)  not null,
    gross_source_amount int           not null check ( gross_source_amount > 0 ),
    commission_percent  decimal(5, 2) not null check ( commission_percent > 0 ),
    net_source_amount   int           not null check ( net_source_amount > 0 ),
    rate                decimal(5, 2) not null check ( rate > 0 ),
    target_amount       int           not null check ( target_amount > 0 ),
    address             varchar(100)  not null,
    primary key (id)
) engine = InnoDB;
alter table withdrawal_request
    add constraint fk_withdrawal_request_client foreign key (client_id) references client (id) on delete cascade on update cascade;

create table api_key
(
    id        bigint      not null auto_increment,
    preview   char(9)     not null,
    name      varchar(30) not null,
    hash      char(64)    not null,
    client_id binary(16)  not null,
    primary key (id)
);
alter table api_key
    add constraint fk_api_key_client foreign key (client_id) references client (id) on delete cascade on update cascade,
    add constraint unique_hash unique (hash);

create table merchant_callback
(
    id                 bigint      not null auto_increment,
    created_at         timestamp(0),
    order_id           binary(16)  not null,
    merchant           varchar(30) not null,
    merchant_order_id  varchar(300),
    status             varchar(50) not null,
    status_description varchar(50),
    primary key (id)
);
alter table merchant_callback
    add constraint fk_merchant_callback_order foreign key (order_id) references orders (id) on delete cascade on update cascade;
