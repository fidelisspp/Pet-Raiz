-- =====================================================================
-- service-produtos - alterações da Etapa 2 sobre o schema que veio do pet-raiz
-- Bancos: pet_produtos_db e pet_produtos_db_test
-- Ordem: schema-produtos.sql -> ESTE -> migracao-de-pet-raiz.sql -> dados-catalogo-produtos.sql
--
-- Decisão 3: só o tipo de pet (filtro "Pet" do app). Ingredientes e validade ficam fora.
--   'both' é o padrão: serve para os produtos migrados, que não tinham essa informação.
-- Imagens: product.image_url passa a guardar só o caminho relativo ("/images/x.jpg").
--   Não muda o tipo da coluna; quem converte os dados antigos é a migração.
-- =====================================================================

ALTER TABLE product
  ADD COLUMN pet_type ENUM('dog', 'cat', 'both') NOT NULL DEFAULT 'both' AFTER category_id;
