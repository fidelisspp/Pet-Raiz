-- =====================================================================
-- Parte "usuários" do antigo pet-raiz/docs/db/dados-catalogo.sql:
-- os 2 produtores aprovados do catálogo (Sítio Boa Vista e Fazenda Raízes).
--
-- Banco: pet_usuarios_db. Rodar DEPOIS de alteracoes-etapa2.sql e da migração, e ANTES
-- do service-produtos/docs/db/dados-catalogo-produtos.sql, que usa estes ids.
--
-- Por que id fixo (1001 e 1002)? No pet-raiz o script usava LAST_INSERT_ID()
-- porque produtor e produto estavam no mesmo banco. Agora os produtos ficam em
-- outro banco e precisam saber o id do produtor antes; um id alto evita colidir
-- com os usuários que forem se cadastrando pelo app.
--
-- Foto (decisão 5): só o caminho relativo. O arquivo é servido pelo service-produtos
-- (http://10.0.2.2:8080/images/...), então precisa estar em
-- service-produtos/src/main/resources/static/images/:
--   produtor-sitio-boa-vista.jpg, produtor-fazenda-raizes.jpg, produtor-sitio-raiz-organicos.jpg
--
-- O UPDATE do final dá foto ao produtor id 2 (Sítio Raiz Orgânicos), que veio da migração
-- do pet_raizdb sem image_url (a coluna não existia lá). Fica aqui, e não na migração,
-- para a migração continuar sendo uma cópia fiel do banco antigo.
--
-- Senha dos dois: 123456
-- =====================================================================

START TRANSACTION;

INSERT INTO app_user (id, name, email, phone, password_hash, user_type) VALUES
  (1001, 'Marina Toledo', 'contato@sitioboavista.dev', '(11) 94567-1201',
   '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'producer'),
  (1002, 'Rafael Nunes', 'contato@fazendaraizes.dev', '(19) 93412-5530',
   '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'producer');

INSERT INTO producer (user_id, cnpj, company_name, technical_responsible_name, short_description, status,
                      image_url, city, state) VALUES
  (1001, '45.781.296/0001-63', 'Sítio Boa Vista', 'Dra. Paula Mendes (CRMV-SP 41.287)',
   'Ração e petiscos orgânicos feitos em pequenos lotes na serra de Atibaia (SP).', 'approved',
   '/images/produtor-sitio-boa-vista.jpg', 'Atibaia', 'SP'),
  (1002, '28.641.953/0001-58', 'Fazenda Raízes', 'Dr. Caio Ferraz (CRMV-SP 38.902)',
   'Acessórios de materiais naturais e óleos prensados a frio, direto do campo em Campinas (SP).', 'approved',
   '/images/produtor-fazenda-raizes.jpg', 'Campinas', 'SP');

-- Produtor migrado do pet-raiz: só preenche se ainda estiver sem foto
UPDATE producer SET image_url = '/images/produtor-sitio-raiz-organicos.jpg'
 WHERE user_id = 2 AND image_url IS NULL;

COMMIT;
