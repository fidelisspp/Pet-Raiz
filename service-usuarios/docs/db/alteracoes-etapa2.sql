-- =====================================================================
-- service-usuarios - alterações da Etapa 2 sobre o schema que veio do pet-raiz
-- Bancos: pet_usuarios_db e pet_usuarios_db_test
-- Ordem: schema-usuarios.sql -> ESTE -> migracao-de-pet-raiz.sql -> dados-catalogo-usuarios.sql
--
-- Decisão 5: dados públicos do produtor (foto e localização), todos opcionais.
--   image_url guarda só o caminho relativo ("/images/x.jpg"); o app junta com a
--   URL base do service-produtos, que é quem serve /images/**.
-- =====================================================================

ALTER TABLE producer
  ADD COLUMN image_url VARCHAR(500) NULL COMMENT 'caminho relativo, ex.: /images/produtor-x.jpg',
  ADD COLUMN city      VARCHAR(100) NULL,
  ADD COLUMN state     CHAR(2)      NULL;
