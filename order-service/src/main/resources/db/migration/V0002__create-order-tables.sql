create table order_service.orders (
    id          bigserial    primary key,
    resource_id varchar(64)  not null unique,
    customer_id varchar(64)  not null,
    status      varchar(32)  not null,
    shipping_address text    not null,
    created_at  timestamptz  not null
);

create table order_service.order_item (
    id          bigserial       primary key,
    order_id    bigint          not null references order_service.orders(id),
    product_id  varchar(64)     not null,
    quantity    int             not null,
    unit_price  numeric(19,4)   not null
);
