-- Phase 4: supplies and stock now belong to the Inventory service, and the
-- execution times of the items belong to the Execution service.
--
-- Items keep a snapshot of the supply (id, name; the price was already stored),
-- so the name is copied before the supplies table is dropped.

ALTER TABLE service_order_items ADD COLUMN supply_name VARCHAR(255);

UPDATE service_order_items i
    JOIN supplies s ON s.id = i.supply_id
SET i.supply_name = s.name;

ALTER TABLE service_order_items DROP FOREIGN KEY fk_items_supply;

ALTER TABLE service_order_items
    DROP COLUMN execution_started_at,
    DROP COLUMN execution_finished_at;

DROP TABLE supplies;
