-- Limpa tudo na ordem das FKs, inclusive linhas criadas pelos próprios testes
DELETE FROM producer_document;
DELETE FROM customer;
DELETE FROM producer;
DELETE FROM admin;
DELETE FROM app_user;
