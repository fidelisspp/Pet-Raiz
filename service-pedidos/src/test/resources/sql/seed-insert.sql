-- Massa de testes do service-pedidos. Só tabelas deste serviço.
-- Clientes (ids do seed do service-usuarios): 101 Bia (ativa) e 102 Bob (suspenso; quem responde
-- isso nos testes é o UsuariosStub). Produtos e estoque: ProdutosStub (mesmos ids do seed do
-- service-produtos: 100, 101, 102, 103 inativo, 106; produtores 103 e 106).

INSERT INTO address (id, customer_id, label, recipient_name, street, number, complement, neighborhood, city, state, zip_code) VALUES
  (500, 101, 'Casa',     'Bia Souza', 'Rua das Flores', '100',  'Apto 12', 'Centro',     'Atibaia',   'SP', '12940-000'),
  (501, 101, 'Trabalho', 'Bia Souza', 'Av. Paulista',   '1000', NULL,      'Bela Vista', 'São Paulo', 'SP', '01310-100'),
  (502, 102, 'Casa',     'Bob Lima',  'Rua B',          '2',    NULL,      'Centro',     'Campinas',  'SP', '13010-000');

-- Carrinho do Bob (suspenso) com um item; a Bia não tem carrinho (é criado no primeiro acesso)
INSERT INTO cart (id, customer_id) VALUES (600, 102);
INSERT INTO cart_item (id, cart_id, product_id, quantity) VALUES (650, 600, 100, 1);

-- 700: pedido do Bob, já pago e enviado (para testar que a Bia não enxerga)
-- 701: pedido da Bia no endereço 501, pagamento pendente (para testar pagamento e a exclusão do endereço)
INSERT INTO purchase_order (id, customer_id, address_id, shipping_method, shipping_amount, total_amount, created_at) VALUES
  (700, 102, 502, 'standard', 12.90, 102.80, '2026-03-01 10:00:00'),
  (701, 101, 501, 'express',  24.90,  84.90, '2026-02-01 10:00:00');

INSERT INTO order_producer_group (id, purchase_order_id, producer_id, delivery_status) VALUES
  (800, 700, 103, 'shipped'),
  (801, 701, 106, 'pending');

INSERT INTO order_item (id, order_producer_group_id, product_id, product_name, product_image_url, quantity, unit_price) VALUES
  (900, 800, 100, 'Ração Orgânica de Frango', '/images/frango.jpg', 1, 89.90),
  (901, 801, 106, 'Mel para Cães',            '/images/mel.jpg',    2, 30.00);

INSERT INTO payment (id, purchase_order_id, payment_method, status, amount, stripe_session_id, created_at, confirmed_at) VALUES
  (950, 700, 'pix',         'paid',    102.80, NULL, '2026-03-01 10:00:00', '2026-03-01 10:05:00'),
  (951, 701, 'credit_card', 'pending',  84.90, NULL, '2026-02-01 10:00:00', NULL);
