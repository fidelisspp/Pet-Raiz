-- =====================================================================
-- service-usuarios - schema MySQL 8 (bancos pet_usuarios_db e pet_usuarios_db_test)
--
-- Tabelas que vieram do pet-raiz (docs/db/schema-banco-dados.sql) SEM mudança de colunas:
--   app_user, customer, producer, admin, producer_document
-- Todas as FKs aqui são internas ao serviço, por isso continuam existindo.
-- Nenhuma outra tabela do pet-raiz fica neste banco.
-- =====================================================================

CREATE TABLE app_user (
  id            INT          NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(255) NOT NULL,
  phone         VARCHAR(20),
  password_hash VARCHAR(255) NOT NULL,
  user_type     ENUM('customer', 'admin', 'producer') NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer (
  user_id   INT         NOT NULL,
  cpf       VARCHAR(14) NOT NULL,
  suspended BOOLEAN     NOT NULL DEFAULT FALSE,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_customer_cpf (cpf)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE producer (
  user_id                    INT          NOT NULL,
  cnpj                       VARCHAR(18)  NOT NULL,
  company_name               VARCHAR(200) NOT NULL,
  technical_responsible_name VARCHAR(150) NOT NULL,
  short_description          VARCHAR(500) NOT NULL,
  status                     ENUM('pending', 'approved', 'rejected') NOT NULL DEFAULT 'pending',
  created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_producer_cnpj (cnpj)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin (
  user_id INT NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE producer_document (
  id           INT          NOT NULL AUTO_INCREMENT,
  producer_id  INT          NOT NULL,
  document_url VARCHAR(500) NOT NULL,
  submitted_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- FKs internas (mesmos nomes do pet-raiz)
ALTER TABLE customer ADD CONSTRAINT fk_customer_user
  FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE producer ADD CONSTRAINT fk_producer_user
  FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE admin ADD CONSTRAINT fk_admin_user
  FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE producer_document ADD CONSTRAINT fk_producer_document_producer
  FOREIGN KEY (producer_id) REFERENCES producer (user_id) ON DELETE NO ACTION ON UPDATE NO ACTION;
