# Pet Raiz — Contratos da API

Fonte para o app Android (`PetRaiz`). Atualizado a cada etapa do roteiro (CLAUDE.md, seção 7).

**Estado:** Etapa 1 (produtor gerencia os próprios produtos).

### Mudanças por etapa

| Etapa | Rotas novas ou alteradas |
|---|---|
| 1 | **Novas:** `GET /produtores/me` (usuários); `GET /produtos/meus`, `GET/PUT/DELETE /produtos/meus/{id}` (produtos). **Alterada (compatível):** `POST /produtos` aceita `imageUrl` ausente → grava `/images/sem-foto.png` |

## Convenções

| Item | Valor |
|---|---|
| URLs base (dev) | usuários `http://<host>:8081`, produtos `http://<host>:8080`, pedidos `http://<host>:8082` |
| Autenticação | header `Authorization: Bearer <token>`; token obtido em `POST /api/v1/auth` (service-usuarios), vale 8 h e é aceito pelos 3 serviços |
| Perfis (`role`) | `CUSTOMER`, `PRODUCER`, `ADMIN` |
| Imagens | `imageUrl` é sempre caminho relativo (`/images/x.jpg`); o app monta `URL_PRODUTOS + imageUrl`. Em produto nunca vem `null` (sem foto = `/images/sem-foto.png`); em produtor pode vir `null` |
| Datas | `LocalDateTime` ISO-8601, ex.: `2026-11-11T14:30:00` (fuso America/Sao_Paulo) |
| Dinheiro | número decimal, ex.: `49.90` |
| Swagger | `/docs-petraiz.html` em cada serviço |

### Página (`PageableDto`)

```json
{ "content": [ ... ], "first": true, "last": false, "page": 0, "size": 10,
  "pageElements": 10, "totalPages": 3, "totalElements": 25 }
```

Parâmetros: `page` (começa em 0), `size`, `sort` (ex.: `price,desc`), quando a rota aceitar.

### Erro (`ErrorMessage`)

```json
{ "path": "/api/v1/usuarios/clientes", "method": "POST", "status": 422,
  "statusText": "Unprocessable Entity", "message": "Campo(s) invalido(s)",
  "errors": { "cpf": "número do registro de contribuinte individual brasileiro (CPF) inválido" } }
```

`errors` (campo → mensagem) só aparece no 422 e em alguns 409 (productId → motivo).

Códigos comuns: **401** token ausente/inválido · **403** perfil sem permissão · **404** não existe (ou é de outro usuário) · **409** conflito de regra · **422** validação · **503** outro serviço fora do ar.

---

## service-usuarios (8081)

### `POST /api/v1/auth` — público
Corpo: `{ "email": "...", "password": "..." }`
200 → `{ "token": "eyJ..." }`
Erros: 400 credenciais inválidas · 403 produtor pendente/rejeitado ou cliente suspenso · 422.

### `POST /api/v1/usuarios/clientes` — público
Corpo: `{ "name": 5..100, "email", "cpf" (com ou sem pontuação), "password": 6..100, "phone"?: ≤20 }`
201 → `{ id, name, email, phone, cpf, suspended }`
Erros: 409 e-mail ou CPF já cadastrado · 422.

### `POST /api/v1/usuarios/produtores` — público
Corpo: `{ name, email, password, phone?, cnpj, companyName, technicalResponsibleName, shortDescription }`
201 → `{ id, name, email, phone, cnpj, companyName, technicalResponsibleName, shortDescription, status: "pending" }`
Erros: 409 e-mail ou CNPJ já cadastrado · 422. Login só após aprovação.

### `GET /api/v1/usuarios/me` — qualquer perfil
200 → `{ id, name, email, phone, role, createdAt }`

### `GET /api/v1/usuarios/{id}` — ADMIN ou o próprio usuário
200 → mesmo formato do `/me`. Erros: 403 · 404.

### `PATCH /api/v1/usuarios/{id}` — só o próprio usuário (troca de senha)
Corpo: `{ currentPassword, newPassword, confirmPassword }` (6..100)
204. Erros: 400 senha atual errada ou confirmação diferente · 403 · 422.

### `GET /api/v1/usuarios` — ADMIN
200 → lista de `{ id, name, email, phone, role, createdAt }`.

### `GET /api/v1/produtores` — público
Paginado (`size` padrão 10, `sort` padrão `companyName,asc`). Só aprovados.
200 → página de `{ id, companyName, shortDescription, cnpj, imageUrl?, city?, state?, since, verified }`

### `GET /api/v1/produtores/me` — PRODUCER *(Etapa 1)*
Perfil do produtor logado (área do produtor no app). `id` é o mesmo do token e do `producerId` dos produtos.
200 →
```json
{ "id": 2, "companyName": "Sítio Raiz Orgânicos", "shortDescription": "Rações naturais orgânicas",
  "imageUrl": "/images/produtor-sitio-raiz-organicos.jpg", "city": null, "state": null, "status": "approved" }
```
`imageUrl`, `city` e `state` podem vir `null`. `status`: `pending` | `approved` | `rejected` (na prática `approved`, porque só aprovado faz login).
Erros: 401 sem token · 403 perfil diferente de PRODUCER.

### `GET /api/v1/produtores/{id}` — público
200 → `{ id, companyName, shortDescription, cnpj, imageUrl?, city?, state?, since, verified }`
Erro: 404 inexistente, pendente ou rejeitado.

### `GET /api/v1/clientes` e `GET /api/v1/clientes/{id}` — ADMIN
Página (padrão 5, `name,asc`) / objeto `{ id, name, email, phone, cpf, suspended }`. Erros: 403 · 404.

---

## service-produtos (8080)

### `GET /api/v1/produtos` — público
Filtros: `nome` (trecho), `categoriaId`, `produtorId`, `petType` (`dog` traz dog+both; `cat` traz cat+both; `both`), `page`, `size` (padrão 10), `sort` (padrão `name,asc`).
Só produtos ativos de produtores aprovados.
200 → página de `ProdutoResponseDto`:
```json
{ "id": 1, "name": "...", "description": "...", "price": 49.90, "stock": 12,
  "imageUrl": "/images/x.jpg", "petType": "dog",
  "category": { "id": 1, "name": "Alimentação" },
  "producer": { "id": 2, "companyName": "...", "cnpj": "...", "verified": true } }
```
Erros: 400 `petType` inválido · 503 service-usuarios fora do ar.

### `GET /api/v1/produtos/{id}` — público
200 → `ProdutoResponseDto`. Erros: 404 inexistente, inativo ou de produtor não aprovado · 503.

### `POST /api/v1/produtos` — PRODUCER
Corpo (`ProdutoCreateDto`): `{ name: ≤150, description?, price: >0 (2 casas), stock: ≥0, imageUrl?: "/images/arquivo.(jpg|jpeg|png|webp)", categoryId, petType?: dog|cat|both }`
- `imageUrl` ausente → grava `/images/sem-foto.png`. String vazia ou caminho inválido → 422.
- `petType` ausente → `both`.

O dono é o id do token.
201 → `ProdutoMeuDto` (abaixo).
Erros: 401 · 403 · 404 categoria · 422.

### Área do produtor — `/api/v1/produtos/meus` — PRODUCER *(Etapa 1)*

Só os produtos do dono do token. Produto de outro produtor, removido ou inexistente → **404** (nunca 403).

| Método e URL | Corpo | Sucesso | Erros |
|---|---|---|---|
| `GET /api/v1/produtos/meus?page=&size=&sort=` | — | 200 página de `ProdutoMeuDto` | 401 · 403 |
| `GET /api/v1/produtos/meus/{id}` | — | 200 `ProdutoMeuDto` | 401 · 403 · 404 |
| `PUT /api/v1/produtos/meus/{id}` | `ProdutoCreateDto` | 200 `ProdutoMeuDto` | 401 · 403 · 404 (produto ou categoria) · 422 |
| `DELETE /api/v1/produtos/meus/{id}` | — | 204 | 401 · 403 · 404 |

- **Lista:** padrão `size=10`, `sort=createdAt,desc` (mais novos primeiro). Inclui estoque 0; não inclui removidos. Não depende do service-usuarios.
- **PUT:** mesmo corpo do POST. `imageUrl` e `petType` **ausentes mantêm os valores atuais** (dá para editar só preço/estoque sem reenviar a foto). Os demais campos são obrigatórios. Pedidos já feitos não mudam.
- **DELETE:** soft delete. O produto some do catálogo e da lista; nos carrinhos aparece com `available=false`. Remover de novo → 404.

`ProdutoMeuDto` (no Swagger aparece como `ProdutoCriadoResponseDto`; também é a resposta do POST):
```json
{ "id": 11, "name": "...", "description": null, "price": 12.50, "stock": 3,
  "imageUrl": "/images/sem-foto.png", "petType": "dog", "active": true,
  "category": { "id": 1, "name": "Ração Natural" }, "producerId": 2 }
```

### `GET /api/v1/categorias` — público
200 → `[ { id, name } ]` em ordem alfabética.

### `GET /images/**` — público
Arquivos de imagem (produtos e fotos de produtores). `/images/sem-foto.png` é a imagem padrão de produto sem foto.

---

## service-pedidos (8082) — todas as rotas exigem CUSTOMER

### Endereços

| Método e URL | Corpo | Sucesso | Erros |
|---|---|---|---|
| `GET /api/v1/enderecos` | — | 200 lista (por id) | — |
| `GET /api/v1/enderecos/{id}` | — | 200 endereço | 404 |
| `POST /api/v1/enderecos` | `EnderecoDto` | 201 endereço | 422 |
| `PUT /api/v1/enderecos/{id}` | `EnderecoDto` | 200 endereço | 404 · 422 |
| `DELETE /api/v1/enderecos/{id}` | — | 204 | 404 · 409 já usado em pedido |

`EnderecoDto`: `{ label?: ≤50, recipientName: ≤100, street: ≤200, number: ≤10, complement?: ≤100, neighborhood, city, state: "SP", zipCode: "00000-000" }`
Resposta: os mesmos campos + `id`. Sem "endereço padrão": o app usa o primeiro da lista.

### Carrinho

| Método e URL | Corpo | Sucesso | Erros |
|---|---|---|---|
| `GET /api/v1/carrinho` | — | 200 carrinho | 503 |
| `POST /api/v1/carrinho/itens` | `{ productId, quantity: 1..10000 }` (SOMA) | 200 carrinho | 404 · 409 estoque/inativo · 422 · 503 |
| `PATCH /api/v1/carrinho/itens/{productId}` | `{ quantity }` (TROCA) | 200 carrinho | 404 · 409 · 422 · 503 |
| `DELETE /api/v1/carrinho/itens/{productId}` | — | 204 | 404 |

Carrinho:
```json
{ "id": 1, "totalQuantity": 3, "subtotal": 99.80,
  "items": [ { "productId": 1, "name": "...", "price": 49.90, "stock": 12, "imageUrl": "/images/x.jpg",
               "producerId": 2, "quantity": 2, "subtotal": 99.80, "available": true } ] }
```
`available=false`: produto inativo/removido; fica no carrinho, fora do subtotal, e bloqueia o checkout (409).

### Pedidos

| Método e URL | Corpo | Sucesso | Erros |
|---|---|---|---|
| `POST /api/v1/pedidos` | `{ addressId, shippingMethod: standard\|express, paymentMethod: pix\|card }` | 201 pedido | 403 suspenso · 404 endereço · 409 estoque · 422 (inclui carrinho vazio) · 500 · 503 |
| `POST /api/v1/pedidos/{id}/pagamento` | — | 200 pedido | 404 · 409 já pago |
| `GET /api/v1/pedidos` | `page`, `size` (padrão 10; mais recentes primeiro) | 200 página de pedidos | — |
| `GET /api/v1/pedidos/{id}` | — | 200 pedido | 404 |

Frete por pedido: `standard` 12.90, `express` 24.90. O checkout compra o carrinho inteiro e o esvazia.

Pedido:
```json
{ "id": 10, "createdAt": "...", "address": { ...EnderecoResponseDto },
  "shippingMethod": "standard", "shippingAmount": 12.90, "itemsAmount": 99.80, "totalAmount": 112.70,
  "payment": { "method": "pix", "status": "pending|paid", "amount": 112.70, "createdAt": "...", "confirmedAt": null },
  "producers": [ { "producerId": 2, "deliveryStatus": "pending",
                   "items": [ { "productId": 1, "productName": "...", "productImageUrl": "/images/x.jpg",
                                "quantity": 2, "unitPrice": 49.90, "subtotal": 99.80 } ],
                   "subtotal": 99.80 } ] }
```
`deliveryStatus` (por produtor): `pending` → `preparing` → `shipped` → `delivered`.

---

## Rotas internas (não usar no app)

Exigem o header `X-Internal-Key`; sem ele → 401.

| Serviço | Rota | Uso |
|---|---|---|
| usuários | `GET /api/v1/interno/produtores/aprovados` | catálogo (produtos) |
| usuários | `GET /api/v1/interno/clientes/{id}` → `{ id, suspended }` | checkout (pedidos) |
| produtos | `GET /api/v1/interno/produtos?ids=1,2` (máx. 100) | carrinho e checkout |
| produtos | `POST /api/v1/interno/estoque/reserva` `{ itens: [{productId, quantity}] }` | checkout (tudo ou nada) |
| produtos | `POST /api/v1/interno/estoque/estorno` (mesma lista) | compensação |
