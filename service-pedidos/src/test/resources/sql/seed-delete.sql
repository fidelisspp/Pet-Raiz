-- Limpa tudo na ordem das FKs, inclusive linhas criadas pelos próprios testes
DELETE FROM payment;
DELETE FROM order_item;
DELETE FROM order_producer_group;
DELETE FROM purchase_order;
DELETE FROM cart_item;
DELETE FROM cart;
DELETE FROM address;
DELETE FROM favorite;
