alter table inventory_service.inventory_reservation drop column if exists saga_id;
alter table inventory_service.inventory_reservation add constraint uq_inventory_reservation_order_id unique (order_id);
