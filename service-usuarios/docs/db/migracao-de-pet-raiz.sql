-- =====================================================================
-- Copia os usuários que hoje estão no pet_raizdb para o pet_usuarios_db.
-- Os IDs são MANTIDOS: é isso que permite ao service-produtos continuar usando
-- product.producer_id = 2 sem nenhuma conversão.
--
-- Pré-requisito: schema-usuarios.sql já aplicado no pet_usuarios_db (vazio).
-- O pet_raizdb só é LIDO (SELECT); nada nele muda.
-- Rodar conectado em qualquer banco (os nomes estão qualificados):
--   Get-Content migracao-de-pet-raiz.sql -Raw -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4
--
-- Hoje isso copia: app_user (2 linhas: admin id 1, produtor id 2), admin (1), producer (1).
-- customer e producer_document estão vazias, mas entram no script para ele servir sempre.
-- Depois do INSERT com id explícito, o InnoDB ajusta o AUTO_INCREMENT para max(id)+1 sozinho.
-- =====================================================================

START TRANSACTION;

INSERT INTO pet_usuarios_db.app_user (id, name, email, phone, password_hash, user_type, created_at)
SELECT id, name, email, phone, password_hash, user_type, created_at FROM pet_raizdb.app_user;

INSERT INTO pet_usuarios_db.admin (user_id)
SELECT user_id FROM pet_raizdb.admin;

INSERT INTO pet_usuarios_db.customer (user_id, cpf, suspended)
SELECT user_id, cpf, suspended FROM pet_raizdb.customer;

INSERT INTO pet_usuarios_db.producer (user_id, cnpj, company_name, technical_responsible_name, short_description, status, created_at)
SELECT user_id, cnpj, company_name, technical_responsible_name, short_description, status, created_at FROM pet_raizdb.producer;

INSERT INTO pet_usuarios_db.producer_document (id, producer_id, document_url, submitted_at)
SELECT id, producer_id, document_url, submitted_at FROM pet_raizdb.producer_document;

COMMIT;
