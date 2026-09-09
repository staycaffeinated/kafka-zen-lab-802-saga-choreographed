create table order_service.saga_state (
    id           bigserial    primary key,
    saga_id      varchar(64)  not null unique,
    order_id     varchar(64)  not null,
    status       varchar(32)  not null,
    current_step varchar(64)  not null,
    started_at   timestamptz  not null,
    updated_at   timestamptz  not null
);
