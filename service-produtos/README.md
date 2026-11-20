# service-produtos (porta 8080)

Parte do Pet Raiz em serviços (ver `../README.md`). Catálogo de produtos, categorias, estoque e
imagens. **Não emite token** e **não tem tabela de usuário**.

## Banco

`pet_produtos_db` (testes: `pet_produtos_db_test`). Montagem e ordem dos scripts em `../README.md`.

## Rodar e testar

```bash
./mvnw spring-boot:run     # sobe em http://localhost:8080 (o catálogo precisa do service-usuarios na 8081)
./mvnw clean test          # e2e contra pet_produtos_db_test; o service-usuarios é trocado por um stub HTTP
```

Swagger: http://localhost:8080/docs-petraiz.html

## Imagens

Arquivos em `src/main/resources/static/images/`, servidos em `GET /images/<arquivo>` (público).
O banco guarda só o caminho relativo (`/images/x.jpg`). Depois de colocar arquivos novos, reinicie
o serviço (o `spring-boot:run` copia `src/main/resources` para `target/classes` ao subir).

## Dependência do service-usuarios

O catálogo (`GET /produtos` e `/produtos/{id}`) faz **uma** chamada a
`GET {petraiz.usuarios.url}/api/v1/interno/produtores/aprovados` por requisição, com o header
`X-Internal-Key`. Ela decide o filtro "só aprovados" e o `companyName`/`cnpj` do produtor.
Se falhar (fora do ar, timeout de 2 s/3 s, erro, JSON inesperado): **503** no formato `ErrorMessage`.
Categorias, cadastro de produto e rotas internas **não** dependem do service-usuarios.

## Token

Validado só pela assinatura (mesma `petraiz.jwt.secret-key`), `exp` e as claims `id` e `role`.
Token sem `id` inteiro ou com `role` fora de CUSTOMER/PRODUCER/ADMIN → 401.

## Endpoints (prefixo `/api/v1`)

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| GET | `/produtos` | público | Catálogo paginado. Filtros: `nome`, `categoriaId`, `produtorId`, `petType` (`dog` = dog+both, `cat` = cat+both, `both` = só both) |
| GET | `/produtos/{id}` | público | Produto ativo de produtor aprovado; senão 404 |
| GET | `/categorias` | público | Categorias em ordem alfabética |
| POST | `/produtos` | PRODUCER | Cadastro; dono = claim `id`. `imageUrl` = `/images/arquivo.(jpg\|jpeg\|png\|webp)`, `petType` opcional (padrão `both`). Resposta traz `producerId` |
| GET | `/interno/produtos?ids=1,2` | `X-Internal-Key` | Produtos existentes (ativos ou não), máx. 100 ids |
| POST | `/interno/estoque/reserva` | `X-Internal-Key` | `{"itens":[{"productId":1,"quantity":2}]}`. Tudo ou nada: 409 (estoque/inativo) ou 404 (inexistente) sem baixar nada |
| POST | `/interno/estoque/estorno` | `X-Internal-Key` | Mesma lista da reserva; devolve as quantidades. Sem idempotência (ver `../service-pedidos/README.md`) |
| GET | `/images/<arquivo>` | público | Imagens de produtos e produtores |
