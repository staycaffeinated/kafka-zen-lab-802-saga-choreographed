create table order_service.outbox (
    id             uuid         primary key default gen_random_uuid(),
    aggregate_type varchar(500) not null,
    aggregate_id   varchar(255) not null,
    type           varchar(255) not null,
    payload        text         not null,
    created_at     timestamptz  not null default now()
);

create index idx_outbox_aggregate_id on order_service.outbox(aggregate_id);
create index idx_outbox_created_at on order_service.outbox(created_at);
