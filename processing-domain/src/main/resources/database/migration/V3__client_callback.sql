create table client_callback
(
    id              bigint        not null auto_increment,
    created_at      timestamp(3)  not null,
    order_id        binary(16)    not null,
    order_status    varchar(30)   not null,
    url             varchar(255)  not null,
    payload         text          not null,
    status          varchar(30)   not null,
    attempts        integer       not null,
    next_attempt_at timestamp(3)  not null,
    last_attempt_at timestamp(3),
    sent_at         timestamp(3),
    primary key (id),
    constraint fk_client_callback_order foreign key (order_id) references orders (id) on delete cascade,
    index idx_client_callback_status_order_id (status, order_id, id),
    index idx_client_callback_status_next_attempt_at (status, next_attempt_at),
    index idx_client_callback_status_last_attempt_at (status, last_attempt_at),
    index idx_client_callback_status_created_at (status, created_at)
);
