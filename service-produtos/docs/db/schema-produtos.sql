-- =====================================================================
-- service-produtos - schema MySQL 8 (bancos pet_produtos_db e pet_produtos_db_test)
--
-- Tabelas que vieram do pet-raiz, sem mudança de colunas: category, product
--
-- FK QUE CRUZAVA SERVIÇOS E FOI REMOVIDA:
--   fk_product_producer  product.producer_id -> producer.user_id (agora no service-usuarios)
--   A coluna producer_id continua, mas é só um INT com o id do usuário produtor.
--   Quem garante que ele existe é o token (só produtor aprovado consegue token) e,
--   na leitura do catálogo, a consulta ao service-usuarios.
--   O índice idx_product_producer substitui o índice que o MySQL criava junto com a FK.
--
-- FK interna que continua: fk_product_category.
-- =====================================================================

CREATE TABLE category (
  id   INT          NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product (
  id          INT           NOT NULL AUTO_INCREMENT,
  producer_id INT           NOT NULL COMMENT 'id do produtor no service-usuarios (sem FK)',
  category_id INT           NOT NULL,
  name        VARCHAR(150)  NOT NULL,
  description TEXT,
  price       DECIMAL(10,2) NOT NULL,
  stock       INT           NOT NULL DEFAULT 0,
  image_url   VARCHAR(500)  NOT NULL,
  active      BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_product_producer (producer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE product ADD CONSTRAINT fk_product_category
  FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
