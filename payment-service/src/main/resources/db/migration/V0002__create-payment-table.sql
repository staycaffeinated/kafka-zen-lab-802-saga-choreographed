create table payment_service.payment (
    id           bigserial       primary key,
    saga_id      varchar(64)     not null unique,
    order_id     varchar(64)     not null,
    amount       numeric(19,4)   not null,
    status       varchar(32)     not null,
    processed_at timestamptz     not null
);
