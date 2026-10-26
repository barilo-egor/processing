create table ip_address
(
    id        bigint      not null auto_increment,
    address   varchar(39) not null,
    client_id binary(16)  not null,
    primary key (id),
    constraint unique_ip_address_client_address unique (client_id, address),
    constraint fk_ip_address_client foreign key (client_id) references client (id) on delete cascade
);
