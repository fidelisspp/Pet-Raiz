-- Massa de testes do service-usuarios. Todas as senhas são '123456' (mesmo hash BCrypt do curso).
-- Só tabelas deste serviço: categorias e produtos agora são massa do service-produtos.

-- Usuários base
INSERT INTO app_user (id, name, email, phone, password_hash, user_type, created_at) VALUES
  (100, 'Ana Admin',        'ana@email.com',    NULL,            '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'admin',    '2026-01-10 10:00:00'),
  (101, 'Bia',      'bia@email.com',    '(11)98888-0001', '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'customer', '2026-01-10 10:00:00'),
  (102, 'Bob',     'bob@email.com',    NULL,            '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'customer', '2026-01-10 10:00:00'),
  (103, 'Carlos',  'carlos@email.com', '(11)97777-0003', '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'producer', '2026-01-10 10:00:00'),
  (104, 'Paula',   'paula@email.com',  NULL,            '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'producer', '2026-01-10 10:00:00'),
  (105, 'Rita',   'rita@email.com',   NULL,            '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'producer', '2026-01-10 10:00:00'),
  (106, 'Davi',   'davi@email.com',   NULL,            '$2a$10$AtWo422MdyRQ1RgPzmJNnuDB7xN0GW38sXT4rnBFBqGnMyVmVEf4O', 'producer', '2026-01-10 10:00:00');

INSERT INTO admin (user_id) VALUES (100);

INSERT INTO customer (user_id, cpf, suspended) VALUES
  (101, '790.744.260-50', FALSE),
  (102, '553.525.170-47', TRUE);

-- Aprovados: 103 (com foto e cidade) e 106 (sem, para testar os campos opcionais).
-- 104 pendente e 105 rejeitado nunca aparecem nas rotas públicas nem na lista interna.
INSERT INTO producer (user_id, cnpj, company_name, technical_responsible_name, short_description, status, created_at, image_url, city, state) VALUES
  (103, '11.222.333/0001-81', 'Sítio Raiz Orgânicos',  'Dra. Helena Souza',  'Rações naturais de frango e peixe orgânicos', 'approved', '2026-01-10 10:00:00', '/images/produtor-sitio-raiz.jpg', 'Atibaia', 'SP'),
  (104, '45.371.984/0001-55', 'Horta da Paula',        'Dr. Marcos Lima',    'Petiscos de vegetais',                        'pending',  '2026-01-10 10:00:00', NULL, NULL, NULL),
  (105, '20.485.112/0001-97', 'Fazenda da Rita',       'Dra. Clara Nunes',   'Biscoitos caninos',                           'rejected', '2026-01-10 10:00:00', NULL, NULL, NULL),
  (106, '28.641.953/0001-58', 'Apiário do Davi',       'Dr. Davi Prado',     'Mel e própolis para pets',                    'approved', '2026-02-15 09:30:00', NULL, NULL, NULL);
