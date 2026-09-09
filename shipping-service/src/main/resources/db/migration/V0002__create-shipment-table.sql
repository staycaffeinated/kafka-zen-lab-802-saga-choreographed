create table shipping_service.shipment (
    id               bigserial    primary key,
    saga_id          varchar(64)  not null unique,
    order_id         varchar(64)  not null,
    shipping_address text         not null,
    status           varchar(32)  not null,
    created_at       timestamptz  not null
);
