-- =====================================================================
-- service-pedidos - alterações da Etapa 2 / Etapa A sobre o schema que veio do pet-raiz
-- Bancos: pet_pedidos_db e pet_pedidos_db_test
-- Ordem: schema-pedidos.sql -> ESTE -> migracao-de-pet-raiz.sql
-- =====================================================================

-- Decisão 1: frete único por pedido, com os valores fixos da tela
--   (standard = R$ 12,90 | express = R$ 24,90). Sem DEFAULT: todo pedido escolhe.
ALTER TABLE purchase_order
  ADD COLUMN shipping_method ENUM('standard', 'express') NOT NULL AFTER address_id,
  ADD COLUMN shipping_amount DECIMAL(10,2)               NOT NULL AFTER shipping_method;

-- Decisão 6: 4 status (Novo = pending, Em preparo = preparing, Enviado = shipped, Entregue = delivered)
ALTER TABLE order_producer_group
  MODIFY COLUMN delivery_status ENUM('pending', 'preparing', 'shipped', 'delivered') NOT NULL DEFAULT 'pending';

-- Decisão G: nome de quem recebe, por endereço ("Casa · Renata Alves").
--   Evita o service-pedidos chamar o service-usuarios só para saber o nome do cliente.
ALTER TABLE address
  ADD COLUMN recipient_name VARCHAR(100) NOT NULL AFTER label;

-- Decisão G: cópia do nome e da imagem do produto no momento da compra.
--   O histórico não muda se o produto for editado ou removido no service-produtos.
ALTER TABLE order_item
  ADD COLUMN product_name      VARCHAR(150) NOT NULL AFTER product_id,
  ADD COLUMN product_image_url VARCHAR(500) NOT NULL AFTER product_name;
