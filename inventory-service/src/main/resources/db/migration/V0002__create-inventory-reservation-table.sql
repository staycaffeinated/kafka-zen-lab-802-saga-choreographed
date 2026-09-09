create table inventory_service.inventory_reservation (
    id          bigserial    primary key,
    saga_id     varchar(64)  not null unique,
    order_id    varchar(64)  not null,
    status      varchar(32)  not null,
    reserved_at timestamptz  not null
);
