create table client
(
    id                    binary(16) not null,
    username              varchar(255) not null,
    registered_at         datetime(6) not null,
    status                varchar(30)  not null,
    callback_url          varchar(255),
    order_timeout_seconds integer      not null,
    primary key (id)
) engine=InnoDB;
alter table client
    add constraint unique_username unique (username);
create table orders
(
    id                    binary(16) not null,
    created_at            datetime(6) not null,
    client_id             bigint       not null,
    internal_id           varchar(255) not null,
    status                varchar(30)  not null,
    amount                integer      not null,
    enable_unique_amount  bit          not null,
    merchant              varchar(30)  not null,
    merchant_order_id     varchar(255) not null,
    merchant_order_status varchar(255) not null,
    callback_url          varchar(255),
    primary key (id)
) engine=InnoDB;
alter table orders
    add constraint unique_internal_id unique (internal_id);
create table support_users
(
    id            bigint       not null auto_increment,
    username      varchar(255) not null,
    registered_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;
alter table support_users
    add constraint unique_username unique (username);
create table transaction
(
    id         binary(16) not null,
    client_id  bigint      not null,
    amount     integer     not null,
    operation  varchar(30) not null,
    type       varchar(30) not null,
    comment    varchar(255),
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;
create table withdrawal_request
(
    id         bigint       not null auto_increment,
    client_id  binary(16)       not null,
    amount     integer      not null,
    created_at datetime(6) not null,
    status     varchar(30)  not null,
    wallet     varchar(255) not null,
    comment    varchar(255),
    primary key (id),
    foreign key (client_id) references client (id)
) engine=InnoDB;
create table api_key
(
    id        bigint      not null auto_increment,
    preview   char(9)     not null,
    name      varchar(30) not null,
    hash      char(64)    not null,
    client_id binary(16) not null,
    primary key (id),
    foreign key (client_id) references client (id)
);
alter table api_key
    add constraint unique_hash unique (hash);