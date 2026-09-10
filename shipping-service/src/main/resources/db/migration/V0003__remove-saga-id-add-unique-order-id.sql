alter table shipping_service.shipment drop column if exists saga_id;
alter table shipping_service.shipment add constraint uq_shipment_order_id unique (order_id);
