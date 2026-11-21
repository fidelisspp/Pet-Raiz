-- =====================================================================
-- service-pedidos - alterações da Etapa 5.5 (pedidos do admin)
-- Bancos: pet_pedidos_db e pet_pedidos_db_test
-- Ordem: schema-pedidos.sql -> alteracoes-etapa2.sql -> ESTE -> migracao-de-pet-raiz.sql
--
-- 1. delivery_status ganha 'canceled': o admin cancela o pedido inteiro (todas as partes).
--    Os valores antigos continuam válidos, então o script roda com dados.
-- 2. purchase_order.canceled_at: quando o admin cancelou (NULL = não cancelado).
-- =====================================================================

ALTER TABLE order_producer_group
  MODIFY COLUMN delivery_status ENUM('pending', 'preparing', 'shipped', 'delivered', 'canceled') NOT NULL DEFAULT 'pending';

ALTER TABLE purchase_order
  ADD COLUMN canceled_at DATETIME NULL COMMENT 'quando o admin cancelou o pedido';
