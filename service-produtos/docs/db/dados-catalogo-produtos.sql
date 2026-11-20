-- =====================================================================
-- Parte "produtos" do antigo pet-raiz/docs/db/dados-catalogo.sql:
-- categoria "Acessórios" (+ as 3 de antes, via INSERT IGNORE) e os 8 produtos.
--
-- Banco: pet_produtos_db. Rodar DEPOIS de alteracoes-etapa2.sql, da migração e de
-- service-usuarios/docs/db/dados-catalogo-usuarios.sql, que cria os produtores
-- 1001 (Sítio Boa Vista) e 1002 (Fazenda Raízes). Sem FK entre os bancos, o MySQL
-- não reclamaria se a ordem fosse trocada, mas os produtos ficariam sem produtor
-- aprovado e não apareceriam no catálogo.
--
-- image_url: só o caminho relativo; o app junta com a URL base do service-produtos
-- (http://10.0.2.2:8080). Arquivos esperados em static/images/ deste serviço:
--   racao-frango-batata-doce.jpg, racao-peixe-gatos.jpg, petisco-batata-doce.jpg,
--   biscoito-abobora-aveia.jpg,   oleo-coco-organico.jpg, comedouro-bambu.jpg,
--   brinquedo-sisal.jpg,          caminha-algodao-cru.jpg
-- pet_type (decisão 3): dog, cat ou both.
-- Estoques variados de propósito: 0 = esgotado, <= 5 = estoque baixo no app.
-- =====================================================================

START TRANSACTION;

INSERT IGNORE INTO category (name) VALUES
  ('Ração Natural'), ('Petiscos'), ('Suplementos'), ('Acessórios');

SET @boa_vista = 1001;
SET @raizes    = 1002;

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @boa_vista, id, 'dog', 'Ração Orgânica Frango e Batata-Doce',
       'Ração seca sem grãos para cães adultos. Frango caipira orgânico como primeiro ingrediente, sem conservantes nem corantes.',
       89.90, 24, '/images/racao-frango-batata-doce.jpg'
  FROM category WHERE name = 'Ração Natural';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @boa_vista, id, 'cat', 'Ração Natural de Peixe para Gatos',
       'Tilápia e sardinha com abóbora orgânica. Rica em ômega 3, para gatos de todas as idades.',
       99.00, 4, '/images/racao-peixe-gatos.jpg'
  FROM category WHERE name = 'Ração Natural';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @boa_vista, id, 'dog', 'Chips de Batata-Doce Desidratada',
       'Petisco de um ingrediente só: batata-doce orgânica fatiada e desidratada. Pacote de 150 g.',
       19.50, 40, '/images/petisco-batata-doce.jpg'
  FROM category WHERE name = 'Petiscos';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @boa_vista, id, 'dog', 'Biscoito de Abóbora e Aveia',
       'Biscoitos assados com abóbora cabotiá e aveia orgânica, sem açúcar. Pacote de 200 g.',
       24.90, 0, '/images/biscoito-abobora-aveia.jpg'
  FROM category WHERE name = 'Petiscos';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @raizes, id, 'both', 'Óleo de Coco Orgânico Extravirgem',
       'Prensado a frio, para misturar na ração. Ajuda na pelagem e na digestão. Frasco de 250 ml.',
       34.90, 15, '/images/oleo-coco-organico.jpg'
  FROM category WHERE name = 'Suplementos';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @raizes, id, 'dog', 'Comedouro de Bambu com Tigela Inox',
       'Suporte de bambu reflorestado com tigela de aço inox removível. Altura ideal para cães de porte médio.',
       79.90, 8, '/images/comedouro-bambu.jpg'
  FROM category WHERE name = 'Acessórios';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @raizes, id, 'both', 'Brinquedo de Sisal Natural',
       'Bolinha trançada à mão com sisal sem tingimento. Boa para gatos e cães de pequeno porte.',
       22.00, 3, '/images/brinquedo-sisal.jpg'
  FROM category WHERE name = 'Acessórios';

INSERT INTO product (producer_id, category_id, pet_type, name, description, price, stock, image_url)
SELECT @raizes, id, 'both', 'Caminha de Algodão Cru',
       'Caminha acolchoada com capa de algodão cru removível e lavável. Tamanho M (60 x 50 cm).',
       149.90, 6, '/images/caminha-algodao-cru.jpg'
  FROM category WHERE name = 'Acessórios';

COMMIT;
