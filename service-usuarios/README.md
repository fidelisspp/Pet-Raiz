# service-usuarios (porta 8081)

Parte do Pet Raiz em serviços (ver `../README.md`). Feito a partir de uma cópia do `pet-raiz`,
mantendo só usuários, autenticação e produtores. É o **único serviço que emite o JWT**.

## Banco

`pet_usuarios_db` (testes: `pet_usuarios_db_test`). Montagem e ordem dos scripts em
`../README.md`. Arquivos em `docs/db/`:

| Script | O que faz |
|---|---|
| `schema-usuarios.sql` | `app_user`, `customer`, `producer`, `admin`, `producer_document` (cópia fiel do pet-raiz) |
| `alteracoes-etapa2.sql` | `producer.image_url`, `city`, `state` (opcionais) |
| `alteracoes-etapa5-1.sql` | `producer.rejection_reason`, `reviewed_at`; `producer_document.original_name`, `content_type`, `size_bytes` |
| `migracao-de-pet-raiz.sql` | copia os usuários do `pet_raizdb` mantendo os IDs (só lê o banco antigo) |
| `dados-catalogo-usuarios.sql` | produtores 1001 (Sítio Boa Vista) e 1002 (Fazenda Raízes), senha `123456` |

## Rodar e testar

```bash
./mvnw spring-boot:run     # sobe em http://localhost:8081
./mvnw clean test          # e2e contra pet_usuarios_db_test
```

Swagger: http://localhost:8081/docs-petraiz.html

## Token

Claims: `sub` (e-mail), `iat`, `exp` (8 h), `role` (CUSTOMER, PRODUCER ou ADMIN) e **`id`**
(id do usuário). O `service-produtos` e o `service-pedidos` não têm tabela de usuário: validam o
token com a mesma `petraiz.jwt.secret-key` e usam `id` e `role` das claims.

## Endpoints (prefixo `/api/v1`)

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/auth` | público | Login. **Contrato do app, inalterado** |
| POST | `/usuarios/clientes` | público | Cadastro de cliente. **Contrato do app, inalterado** |
| POST | `/usuarios/produtores` | público | Cadastro de produtor (`pending`). JSON, ou multipart com as partes `dados` (o mesmo JSON) e `documento` (qualquer formato, até 5 MB) |
| GET | `/usuarios/me` | autenticado | Usuário logado. **Contrato do app, inalterado** |
| GET | `/usuarios/{id}` | ADMIN ou o próprio | Busca usuário |
| PATCH | `/usuarios/{id}` | o próprio | Troca de senha |
| GET | `/usuarios` | ADMIN | Lista usuários |
| GET | `/clientes`, `/clientes/{id}` | ADMIN | Clientes. A lista aceita `busca` (nome ou e-mail) e `suspenso` (true/false) (Etapa 5.3) |
| PATCH | `/clientes/{id}/suspensao` | ADMIN | `{suspended}`: suspende ou reativa; suspenso não faz login nem finaliza compra (Etapa 5.3) |
| GET | `/produtores/me` | PRODUCER | Perfil do produtor logado `{id, companyName, shortDescription, imageUrl, city, state, status}`. A regra de segurança vem **antes** do `permitAll` de `/produtores/*` |
| GET | `/produtores` | público | Produtores **aprovados**, paginado (`page`, `size`, `sort`; padrão `companyName`) |
| GET | `/produtores/{id}` | público | Produtor aprovado; pendente, rejeitado ou inexistente → 404 |
| GET | `/produtores/pendentes` | ADMIN | Cadastros `pending`, mais antigos primeiro (fila de análise). Regra de segurança **antes** do `permitAll` de `/produtores/*` |
| GET | `/produtores/cadastros?status=` | ADMIN | Cadastros por status (`pending`, `approved`, `rejected`; sem filtro = todos), mais recentes primeiro |
| PATCH | `/produtores/{id}/status` | ADMIN | `{status, reason?}`: `pending→approved`, `pending→rejected` (reason obrigatório), `rejected→pending` (reabrir); outras → 409 |
| GET | `/produtores/{id}/documento` | ADMIN | Baixa o documento enviado no cadastro (nome original no `Content-Disposition`) |
| GET | `/interno/produtores/aprovados` | header `X-Internal-Key` | Todos os aprovados `[{id, companyName, cnpj}]`, para o service-produtos |
| GET | `/interno/clientes/{id}` | header `X-Internal-Key` | `{id, suspended}`, para o checkout do service-pedidos. Id que não é de cliente → 404 |
| GET | `/interno/clientes?ids=` ou `?busca=` | header `X-Internal-Key` | `[{id, name}]` (até 100), para a lista de pedidos do admin no service-pedidos (Etapa 5.5). Sem nenhum dos dois → 400 |

Resposta pública do produtor: `id, companyName, shortDescription, cnpj, imageUrl, city, state,
since, verified`. `imageUrl` é um caminho relativo (`/images/x.jpg`): o app junta com a URL base
do **service-produtos** (8080), que é quem serve as imagens.

## Documentos do cadastro (Etapa 5.1)

O arquivo enviado no cadastro vai para `<petraiz.uploads.dir>/documentos/<uuid>` (padrão `./uploads`,
fora de `src/main/resources`). A pasta **não** é servida como estático: o nome original fica só no
banco (`producer_document.original_name`) e o conteúdo sai apenas por `GET /produtores/{id}/documento`
(ADMIN). Qualquer formato é aceito; o limite de 5 MB vem de `spring.servlet.multipart.max-file-size`.

O login de produtor rejeitado devolve 403 com `"Cadastro de produtor rejeitado. Motivo: <reason>"`.

## Rotas internas

`/api/v1/interno/**` só aceita o header `X-Internal-Key` com o valor de `petraiz.interno.chave`
(o filtro é o `config/InternoKeyFilter`). Sem a chave, ou com a chave errada, responde 401;
um Bearer token de usuário **não** abre essas rotas. O valor padrão do `application.properties` é
só para desenvolvimento: em outro ambiente, defina a variável `INTERNAL_KEY` (e `JWT_SECRET`)
com o mesmo valor em todos os serviços.

## Diferenças em relação ao pet-raiz

- Saíram: produtos e categorias (entities, repositories, services, controllers, DTOs, mappers e testes).
- `JwtUtils.createToken` ganhou a claim `id`.
- Novos: `ProdutorController`, `InternoController`, `InternoKeyFilter`, `ProdutorIT`, `ClienteInternoIT`.
- Etapa 5.1: `ProducerDocument`, `DocumentoService`, rotas de análise no `ProdutorController`, `AnaliseProdutorIT`.
- `ClienteIT`: o nome esperado agora bate com o seed (`"Bob"`).
