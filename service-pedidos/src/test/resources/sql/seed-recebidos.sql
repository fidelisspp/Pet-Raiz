-- Massa extra só do PedidoRecebidoIT (roda DEPOIS do seed-insert.sql; o seed-delete.sql limpa tudo).
-- Produtor 103 (Carlos) recebeu: 800 (pedido 700 do Bob, pago, shipped), 802 (pedido 702, pago, pending)
-- e 804 (pedido 703, pago, delivered). Produtor 106 (Davi): 803 (pedido 702, pago, preparing) e
-- 801 (pedido 701, pagamento PENDENTE: não pode aparecer para o produtor).

INSERT INTO purchase_order (id, customer_id, address_id, shipping_method, shipping_amount, total_amount, created_at) VALUES
  (702, 101, 500, 'standard', 12.90, 242.20, '2026-04-01 10:00:00'),
  (703, 101, 501, 'express',  24.90, 123.90, '2026-05-01 10:00:00');

INSERT INTO order_producer_group (id, purchase_order_id, producer_id, delivery_status) VALUES
  (802, 702, 103, 'pending'),
  (803, 702, 106, 'preparing'),
  (804, 703, 103, 'delivered');

INSERT INTO order_item (id, order_producer_group_id, product_id, product_name, product_image_url, quantity, unit_price) VALUES
  (902, 802, 100, 'Ração Orgânica de Frango', '/images/frango.jpg', 2, 89.90),
  (903, 802, 101, 'Petisco de Batata Doce',   '/images/batata.jpg', 1, 19.50),
  (904, 803, 106, 'Mel para Cães',            '/images/mel.jpg',    1, 30.00),
  (905, 804, 102, 'Ração Orgânica de Peixe',  '/images/peixe.jpg',  1, 99.00);

INSERT INTO payment (id, purchase_order_id, payment_method, status, amount, stripe_session_id, created_at, confirmed_at) VALUES
  (952, 702, 'pix',         'paid', 242.20, NULL, '2026-04-01 10:00:00', '2026-04-01 10:02:00'),
  (953, 703, 'credit_card', 'paid', 123.90, NULL, '2026-05-01 10:00:00', '2026-05-01 10:01:00');
