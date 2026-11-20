-- =====================================================================
-- service-pedidos - schema MySQL 8 (bancos pet_pedidos_db e pet_pedidos_db_test)
--
-- Tabelas que vieram do pet-raiz, sem mudança de colunas:
--   address, cart, cart_item, purchase_order, order_producer_group, order_item, payment, favorite
--   (favorite: só a tabela, sem endpoints por enquanto; decisão D)
--
-- FKs QUE CRUZAVAM SERVIÇOS E FORAM REMOVIDAS (as colunas continuam, como INT simples):
--   fk_address_customer               address.customer_id              -> customer (usuarios)
--   fk_cart_customer                  cart.customer_id                 -> customer (usuarios)
--   fk_purchase_order_customer        purchase_order.customer_id       -> customer (usuarios)
--   fk_favorite_customer              favorite.customer_id             -> customer (usuarios)
--   fk_order_producer_group_producer  order_producer_group.producer_id -> producer (usuarios)
--   fk_cart_item_product              cart_item.product_id             -> product  (produtos)
--   fk_order_item_product             order_item.product_id            -> product  (produtos)
--   fk_favorite_product               favorite.product_id              -> product  (produtos)
--   customer_id = claim "id" do token (perfil CUSTOMER); producer_id e product_id vêm das
--   respostas do service-produtos. Cada uma ganhou um índice idx_* no lugar do da FK.
--
-- FKs internas que continuam: fk_payment_purchase_order, fk_purchase_order_address,
--   fk_order_producer_group_purchase_order, fk_order_item_group, fk_cart_item_cart.
--
-- Este arquivo é a cópia fiel do pet-raiz. As colunas novas (frete, recipient_name,
-- cópias do produto no item, status 'preparing') estão em alteracoes-etapa2.sql,
-- que deve rodar logo depois deste.
-- =====================================================================

CREATE TABLE address (
  id           INT          NOT NULL AUTO_INCREMENT,
  customer_id  INT          NOT NULL COMMENT 'id do cliente no service-usuarios (sem FK)',
  label        VARCHAR(50),
  street       VARCHAR(200) NOT NULL,
  number       VARCHAR(10)  NOT NULL,
  complement   VARCHAR(100),
  neighborhood VARCHAR(100) NOT NULL,
  city         VARCHAR(100) NOT NULL,
  state        CHAR(2)      NOT NULL,
  zip_code     VARCHAR(9)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_address_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_order (
  id           INT           NOT NULL AUTO_INCREMENT,
  customer_id  INT           NOT NULL COMMENT 'id do cliente no service-usuarios (sem FK)',
  address_id   INT           NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_purchase_order_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_producer_group (
  id                INT NOT NULL AUTO_INCREMENT,
  purchase_order_id INT NOT NULL,
  producer_id       INT NOT NULL COMMENT 'id do produtor no service-usuarios (sem FK)',
  delivery_status   ENUM('pending', 'shipped', 'delivered') NOT NULL DEFAULT 'pending',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_producer_group (purchase_order_id, producer_id),
  KEY idx_order_producer_group_producer (producer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item (
  id                      INT           NOT NULL AUTO_INCREMENT,
  order_producer_group_id INT           NOT NULL,
  product_id              INT           NOT NULL COMMENT 'id do produto no service-produtos (sem FK)',
  quantity                INT           NOT NULL,
  unit_price              DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment (
  id                INT           NOT NULL AUTO_INCREMENT,
  purchase_order_id INT           NOT NULL,
  payment_method    ENUM('credit_card', 'pix') NOT NULL,
  status            ENUM('pending', 'paid', 'failed', 'refunded') NOT NULL DEFAULT 'pending',
  amount            DECIMAL(10,2) NOT NULL,
  stripe_session_id VARCHAR(255),
  created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at      TIMESTAMP     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_purchase_order (purchase_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart (
  id          INT NOT NULL AUTO_INCREMENT,
  customer_id INT NOT NULL COMMENT 'id do cliente no service-usuarios (sem FK)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cart_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart_item (
  id         INT NOT NULL AUTO_INCREMENT,
  cart_id    INT NOT NULL,
  product_id INT NOT NULL COMMENT 'id do produto no service-produtos (sem FK)',
  quantity   INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cart_item (cart_id, product_id),
  KEY idx_cart_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE favorite (
  id          INT       NOT NULL AUTO_INCREMENT,
  customer_id INT       NOT NULL COMMENT 'id do cliente no service-usuarios (sem FK)',
  product_id  INT       NOT NULL COMMENT 'id do produto no service-produtos (sem FK)',
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite (customer_id, product_id),
  KEY idx_favorite_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- FKs internas (mesmos nomes do pet-raiz)
ALTER TABLE payment ADD CONSTRAINT fk_payment_purchase_order
  FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE purchase_order ADD CONSTRAINT fk_purchase_order_address
  FOREIGN KEY (address_id) REFERENCES address (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE order_producer_group ADD CONSTRAINT fk_order_producer_group_purchase_order
  FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE order_item ADD CONSTRAINT fk_order_item_group
  FOREIGN KEY (order_producer_group_id) REFERENCES order_producer_group (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE cart_item ADD CONSTRAINT fk_cart_item_cart
  FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
