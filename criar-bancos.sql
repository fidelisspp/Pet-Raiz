-- =====================================================================
-- Pet Raiz em 3 serviços: cria os 6 bancos (dev + test) no mesmo MySQL.
-- Rodar UMA vez, como root. A ordem completa dos scripts (schema, alteracoes-etapa2,
-- migrações e seeds) está no README.md desta pasta.
--
-- O pet_raizdb antigo NÃO é tocado: continua como referência.
-- =====================================================================

CREATE DATABASE pet_usuarios_db       CHARACTER SET utf8mb4;
CREATE DATABASE pet_usuarios_db_test  CHARACTER SET utf8mb4;
CREATE DATABASE pet_produtos_db       CHARACTER SET utf8mb4;
CREATE DATABASE pet_produtos_db_test  CHARACTER SET utf8mb4;
CREATE DATABASE pet_pedidos_db        CHARACTER SET utf8mb4;
CREATE DATABASE pet_pedidos_db_test   CHARACTER SET utf8mb4;
