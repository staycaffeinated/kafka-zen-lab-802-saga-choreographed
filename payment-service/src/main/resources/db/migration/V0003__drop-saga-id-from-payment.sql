alter table payment_service.payment drop column if exists saga_id;
alter table payment_service.payment add constraint uq_payment_order_id unique (order_id);
