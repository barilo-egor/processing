alter table client
    modify column callback_url varchar(2048);

alter table orders
    modify column callback_url varchar(2048);

alter table client_callback
    modify column url varchar(2048) not null;
