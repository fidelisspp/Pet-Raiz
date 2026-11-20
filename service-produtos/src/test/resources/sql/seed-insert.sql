-- Massa de testes do service-produtos. Só tabelas deste serviço.
-- Os produtores (producer_id) são os ids do seed do service-usuarios; quem diz quais estão
-- aprovados nos testes é o UsuariosStub: 103 (Sítio Raiz Orgânicos) e 106 (Apiário do Davi).
-- 104 (pendente) e 105 (rejeitado) não vêm na lista, então os produtos deles somem do catálogo.

INSERT INTO category (id, name) VALUES
  (10, 'Ração Natural'),
  (20, 'Petiscos');

-- Catálogo visível: 100, 101, 102 e 106 (ativos e de produtor aprovado)
INSERT INTO product (id, producer_id, category_id, pet_type, name, description, price, stock, image_url, active, created_at) VALUES
  (100, 103, 10, 'dog',  'Ração Orgânica de Frango',     'Frango caipira e legumes', 89.90, 10, '/images/frango.jpg',   TRUE,  '2026-01-10 10:00:00'),
  (101, 103, 20, 'both', 'Petisco de Batata Doce',       'Chips desidratados',       19.50, 30, '/images/batata.jpg',   TRUE,  '2026-01-10 10:00:00'),
  (102, 103, 10, 'cat',  'Ração Orgânica de Peixe',      NULL,                       99.00,  0, '/images/peixe.jpg',    TRUE,  '2026-01-10 10:00:00'),
  (103, 103, 20, 'dog',  'Biscoito Descontinuado',       'Produto inativo',          15.00,  5, '/images/biscoito.jpg', FALSE, '2026-01-10 10:00:00'),
  (104, 104, 10, 'dog',  'Ração da Horta Pendente',      'Produtor em análise',      70.00,  8, '/images/horta.jpg',    TRUE,  '2026-01-10 10:00:00'),
  (105, 105, 20, 'cat',  'Petisco da Fazenda Rejeitada', 'Produtor rejeitado',       12.00,  4, '/images/fazenda.jpg',  TRUE,  '2026-01-10 10:00:00'),
  (106, 106, 20, 'dog',  'Mel para Cães',                'Mel puro',                 30.00,  5, '/images/mel.jpg',      TRUE,  '2026-02-15 09:30:00');
