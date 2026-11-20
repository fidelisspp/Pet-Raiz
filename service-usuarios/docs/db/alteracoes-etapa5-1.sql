-- =====================================================================
-- service-usuarios - alterações da Etapa 5.1 (aprovação de produtor)
-- Bancos: pet_usuarios_db e pet_usuarios_db_test
-- Ordem: schema-usuarios.sql -> alteracoes-etapa2.sql -> ESTE -> migracao-de-pet-raiz.sql -> dados-catalogo-usuarios.sql
--
-- 1. producer: resultado da análise do admin.
--    rejection_reason: motivo obrigatório ao rejeitar; o produtor vê no 403 do login.
--    reviewed_at: quando o admin aprovou/rejeitou (volta a NULL se a análise for reaberta).
-- 2. producer_document (já existia no schema, sem uso): o documento enviado no cadastro.
--    document_url guarda só o NOME do arquivo salvo em <petraiz.uploads.dir>/documentos/ (UUID);
--    o arquivo nunca é servido como estático: só o admin baixa, por GET /produtores/{id}/documento.
-- Todas as colunas novas aceitam linhas antigas (NULL ou valor padrão), então o script roda com dados.
-- =====================================================================

ALTER TABLE producer
  ADD COLUMN rejection_reason VARCHAR(500) NULL COMMENT 'motivo da rejeição (só quando status = rejected)',
  ADD COLUMN reviewed_at      DATETIME     NULL COMMENT 'quando o admin aprovou ou rejeitou';

ALTER TABLE producer_document
  ADD COLUMN original_name VARCHAR(255) NOT NULL DEFAULT 'documento' COMMENT 'nome do arquivo no aparelho do produtor',
  ADD COLUMN content_type  VARCHAR(150) NOT NULL DEFAULT 'application/octet-stream',
  ADD COLUMN size_bytes    BIGINT       NOT NULL DEFAULT 0;
