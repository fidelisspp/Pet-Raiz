-- =====================================================================
-- Copia categorias e produtos do pet_raizdb para o pet_produtos_db, mantendo os IDs.
-- product.producer_id é copiado como está (hoje = 2, o produtor de exemplo), e esse
-- id existe no pet_usuarios_db porque a migração do service-usuarios também mantém IDs.
--
-- Pré-requisito: schema-produtos.sql + alteracoes-etapa2.sql aplicados no pet_produtos_db (vazio).
-- O pet_raizdb só é LIDO.
-- Hoje isso copia: category (3 linhas) e product (2 linhas).
--
-- Duas conversões na cópia:
--   image_url: o pet-raiz guardava URL completa (ex.: https://exemplo.com/frango.jpg).
--              Agora o banco guarda só o caminho relativo: fica "/images/frango.jpg"
--              (o nome do arquivo é o que vem depois da última "/").
--              Os arquivos frango.jpg e batata.jpg precisam existir em static/images/.
--   pet_type:  não existia; fica 'both' (DEFAULT da coluna).
-- =====================================================================

START TRANSACTION;

INSERT INTO pet_produtos_db.category (id, name)
SELECT id, name FROM pet_raizdb.category;

INSERT INTO pet_produtos_db.product (id, producer_id, category_id, name, description, price, stock, image_url, active, created_at)
SELECT id, producer_id, category_id, name, description, price, stock,
       CONCAT('/images/', SUBSTRING_INDEX(image_url, '/', -1)),
       active, created_at
  FROM pet_raizdb.product;

COMMIT;
