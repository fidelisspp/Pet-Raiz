-- =====================================================================
-- Copia as tabelas de compra do pet_raizdb para o pet_pedidos_db, mantendo os IDs.
-- HOJE TODAS ESTÃO VAZIAS no pet_raizdb (conferido em 06/11/2026), então este script
-- não copia nada; ele existe para o caso de surgirem dados antes da migração.
--
-- Pré-requisito: schema-pedidos.sql + alteracoes-etapa2.sql aplicados no pet_pedidos_db.
-- O pet_raizdb só é LIDO. Ordem = ordem das FKs internas (pai antes de filho).
--
-- Colunas novas (que o pet-raiz não tinha) e de onde vêm na cópia:
--   address.recipient_name        <- nome do cliente (pet_raizdb.app_user.name)
--   purchase_order.shipping_*     <- 'standard' e 0.00 (o pet-raiz não cobrava frete)
--   order_item.product_name/_url  <- dados atuais do produto (pet_raizdb.product),
--                                    com a imagem convertida para caminho relativo
--   delivery_status               <- os 3 valores antigos existem no ENUM novo
-- =====================================================================

START TRANSACTION;

INSERT INTO pet_pedidos_db.address
  (id, customer_id, label, recipient_name, street, number, complement, neighborhood, city, state, zip_code)
SELECT a.id, a.customer_id, a.label, u.name, a.street, a.number, a.complement, a.neighborhood, a.city, a.state, a.zip_code
  FROM pet_raizdb.address a
  JOIN pet_raizdb.app_user u ON u.id = a.customer_id;

INSERT INTO pet_pedidos_db.purchase_order
  (id, customer_id, address_id, shipping_method, shipping_amount, total_amount, created_at)
SELECT id, customer_id, address_id, 'standard', 0.00, total_amount, created_at
  FROM pet_raizdb.purchase_order;

INSERT INTO pet_pedidos_db.order_producer_group (id, purchase_order_id, producer_id, delivery_status)
SELECT id, purchase_order_id, producer_id, delivery_status
  FROM pet_raizdb.order_producer_group;

INSERT INTO pet_pedidos_db.order_item
  (id, order_producer_group_id, product_id, product_name, product_image_url, quantity, unit_price)
SELECT oi.id, oi.order_producer_group_id, oi.product_id, p.name,
       CONCAT('/images/', SUBSTRING_INDEX(p.image_url, '/', -1)),
       oi.quantity, oi.unit_price
  FROM pet_raizdb.order_item oi
  JOIN pet_raizdb.product p ON p.id = oi.product_id;

INSERT INTO pet_pedidos_db.payment
  (id, purchase_order_id, payment_method, status, amount, stripe_session_id, created_at, confirmed_at)
SELECT id, purchase_order_id, payment_method, status, amount, stripe_session_id, created_at, confirmed_at
  FROM pet_raizdb.payment;

INSERT INTO pet_pedidos_db.cart (id, customer_id)
SELECT id, customer_id FROM pet_raizdb.cart;

INSERT INTO pet_pedidos_db.cart_item (id, cart_id, product_id, quantity)
SELECT id, cart_id, product_id, quantity FROM pet_raizdb.cart_item;

INSERT INTO pet_pedidos_db.favorite (id, customer_id, product_id, created_at)
SELECT id, customer_id, product_id, created_at FROM pet_raizdb.favorite;

COMMIT;
