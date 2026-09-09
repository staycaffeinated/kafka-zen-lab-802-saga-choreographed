create table order_service.idempotency_key (
    id         bigserial    primary key,
    key        varchar(128) not null unique,
    order_id   varchar(64)  not null,
    created_at timestamptz  not null
);
