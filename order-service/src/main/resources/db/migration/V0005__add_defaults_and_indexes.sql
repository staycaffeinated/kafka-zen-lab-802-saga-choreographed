alter table order_service.orders      alter column created_at set default now();
alter table order_service.saga_state  alter column started_at set default now();
alter table order_service.saga_state  alter column updated_at set default now();

create index idx_saga_state_order_id on order_service.saga_state (order_id);
create index idx_order_item_order_id  on order_service.order_item (order_id);
