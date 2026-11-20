# Pet Raiz em serviços

Back-end do **Raiz Pet** (marketplace de produtos naturais para pets) dividido em 3 serviços
Kotlin + Spring Boot independentes. Projeto final da disciplina de Programação Orientada a
Serviços (Senac). O cliente é um app Android nativo, que só conversa com estes serviços por JSON.

> **Um app, três serviços, três bancos, um token.**

O monolito original (`Documents/pet-raiz`) fica intacto como referência; se precisar rodá-lo
junto, suba-o na porta **8090** (`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090`),
nunca na 8080.

## Arquitetura

```
                         App Android (XML)
                 │ JSON + Authorization: Bearer <JWT>
     ┌───────────┼────────────────────────┬───────────────────────────┐
     ▼           ▼                        ▼                           │
┌──────────────────┐   ┌───────────────────────┐   ┌──────────────────────────┐
│ service-usuarios │   │ service-produtos      │   │ service-pedidos          │
│ :8081            │   │ :8080                 │   │ :8082                    │
│ emite o JWT      │◄──┤ catálogo, estoque,    │◄──┤ carrinho, endereços,     │
│ clientes,        │   │ área do produtor,     │   │ pedidos, pagamento,      │
│ produtores, admin│◄──┼───────────────────────┼───┤ favoritos, pedidos       │
│                  │   │ imagens (/images/**)  │   │ recebidos (produtor)     │
└────────┬─────────┘   └──────────┬────────────┘   └────────────┬─────────────┘
         ▼                        ▼                             ▼
  pet_usuarios_db          pet_produtos_db               pet_pedidos_db
```

As setas `◄──` são chamadas HTTP síncronas entre serviços, nas rotas `/api/v1/interno/**`.

| Serviço | Porta | Banco (dev / teste) | Responsável por |
|---|---|---|---|
| `service-usuarios` | 8081 | `pet_usuarios_db` / `_test` | login (emite o JWT), clientes, produtores, admin, `/me`, produtores públicos, `/produtores/me` |
| `service-produtos` | 8080 | `pet_produtos_db` / `_test` | catálogo, categorias, estoque, área do produtor (`/produtos/meus`), upload de fotos, `/images/**` |
| `service-pedidos` | 8082 | `pet_pedidos_db` / `_test` | endereços, carrinho, checkout, pagamento simulado, favoritos, pedidos recebidos pelo produtor |

### Regras da arquitetura

- **Um banco por serviço.** Nenhum serviço lê tabela de outro. Referência entre serviços é um id
  simples, sem FK nem relação JPA (ex.: `product.producer_id`, `cart.customer_id`).
- **Um token.** Só o `service-usuarios` emite o JWT (HS256, segredo compartilhado, expira em 8 h,
  claims `sub` = e-mail, `id` e `role` = `CUSTOMER` | `PRODUCER` | `ADMIN`). Os outros validam a
  assinatura e usam só as claims, sem banco de usuários.
- **Chamadas entre serviços:** HTTP síncrono com `RestClient`, URL base no `application.properties`.
  As rotas `/api/v1/interno/**` exigem o header `X-Internal-Key` e nunca são usadas pelo app.
- **Falha isolada:** se um serviço chamado está fora do ar, quem chamou responde **503** no formato
  `ErrorMessage` e continua de pé.
- **Erros** sempre no formato `ErrorMessage` (`message` e, no 422, `errors` campo → mensagem).
- **Imagens:** o banco guarda só o caminho relativo (`/images/x.jpg`); o app junta com a URL do
  `service-produtos`.

### Comunicação entre serviços (o que mostrar na apresentação)

| Fluxo | Quem chama | Rota interna | Para quê |
|---|---|---|---|
| Catálogo | produtos → usuários | `GET /interno/produtores/aprovados` | Uma chamada por requisição: filtra os produtos por produtor aprovado e traz o nome do produtor |
| Checkout | pedidos → usuários | `GET /interno/clientes/{id}` | Recusa cliente suspenso (403) |
| Checkout | pedidos → produtos | `POST /interno/estoque/reserva` | Reserva tudo ou nada (`SELECT ... FOR UPDATE`) e devolve nome, preço e foto do momento |
| Compensação | pedidos → produtos | `POST /interno/estoque/estorno` | Se o pedido não for gravado depois da reserva, devolve o estoque (ver `service-pedidos/README.md`) |
| Carrinho e favoritos | pedidos → produtos | `GET /interno/produtos?ids=` | Dados atuais dos produtos, uma chamada para a lista inteira |

## Como subir

### Pré-requisitos

- JDK 17 ou mais novo e MySQL 8 local. Os serviços usam Spring Boot 4.1. Cada serviço tem o seu
  `mvnw`, então não precisa instalar o Maven.
- Os bancos montados (próxima seção).
- A variável **`DB_PASSWORD`** com a senha do MySQL. A senha não fica no código.
  - **IntelliJ:** Run Configuration → *Environment variables* → `DB_PASSWORD=<sua senha>`.
  - **Terminal (PowerShell):** `$env:DB_PASSWORD="<sua senha>"`.

### Ordem

Suba nesta ordem, cada um na sua janela (IntelliJ ou terminal):

```powershell
cd service-usuarios;  .\mvnw spring-boot:run   # 8081
cd service-produtos;  .\mvnw spring-boot:run   # 8080 (catálogo precisa do 8081)
cd service-pedidos;   .\mvnw spring-boot:run   # 8082 (checkout precisa do 8081 e do 8080)
```

A ordem só importa para as chamadas entre serviços: cada um sobe sozinho, e o que depende de outro
responde 503 enquanto ele não estiver no ar.

### Variáveis de ambiente

| Variável | Obrigatória | Padrão de dev | Onde |
|---|---|---|---|
| `DB_PASSWORD` | **sim** | — | os 3 |
| `DB_USERNAME` | não | `root` | os 3 |
| `JWT_SECRET` | não | segredo de dev (≥ 32 caracteres) | os 3 (precisa ser igual nos 3) |
| `INTERNAL_KEY` | não | `troque-esta-chave-interna-petraiz` | os 3 (precisa ser igual nos 3) |
| `USUARIOS_URL` | não | `http://localhost:8081` | produtos e pedidos |
| `PRODUTOS_URL` | não | `http://localhost:8080` | pedidos |
| `UPLOADS_DIR` | não | `./uploads` (relativo à pasta onde o serviço roda) | produtos |

Em produção, `JWT_SECRET` e `INTERNAL_KEY` devem ser trocados.

### Documentação da API

- **Swagger:** `http://localhost:<porta>/docs-petraiz.html` em cada serviço (8081, 8080, 8082).
- **[`docs/API-CONTRATOS.md`](docs/API-CONTRATOS.md):** todas as rotas que o app usa (método, URL,
  perfil, corpo, resposta, erros) e o histórico de mudanças por etapa.
- **README de cada serviço:** detalhes internos (ex.: compensação do estoque no `service-pedidos`).

## Montar os bancos (ordem importa)

No PowerShell, cada script é enviado assim (troque `<arquivo>` e `<banco>`; o `mysql` fica em
`C:\Program Files\MySQL\MySQL Server 8.0\bin`):

```powershell
Get-Content <arquivo> -Raw -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4 <banco>
```

| # | Script | Banco(s) |
|---|---|---|
| 1 | `criar-bancos.sql` | (nenhum: cria os 6 bancos) |
| 2 | `service-usuarios/docs/db/schema-usuarios.sql` + `alteracoes-etapa2.sql` | `pet_usuarios_db` e `pet_usuarios_db_test` |
| 3 | `service-produtos/docs/db/schema-produtos.sql` + `alteracoes-etapa2.sql` | `pet_produtos_db` e `pet_produtos_db_test` |
| 4 | `service-pedidos/docs/db/schema-pedidos.sql` + `alteracoes-etapa2.sql` | `pet_pedidos_db` e `pet_pedidos_db_test` |
| 5 | `service-usuarios/docs/db/migracao-de-pet-raiz.sql` | (nomes qualificados; lê `pet_raizdb`) |
| 6 | `service-produtos/docs/db/migracao-de-pet-raiz.sql` | idem |
| 7 | `service-pedidos/docs/db/migracao-de-pet-raiz.sql` | idem |
| 8 | `service-usuarios/docs/db/dados-catalogo-usuarios.sql` | `pet_usuarios_db` |
| 9 | `service-produtos/docs/db/dados-catalogo-produtos.sql` | `pet_produtos_db` |

As migrações (5 a 7) copiam os dados do banco do monolito (`pet_raizdb`) mantendo os IDs, que é o
que mantém `product.producer_id` válido sem FK, e só fazem `SELECT` nele. Os bancos `_test` recebem
só schema + alterações: os dados de teste vêm do `@Sql` de cada serviço.

As etapas 1 a 5 do roteiro não alteraram tabelas: não há script novo depois do `alteracoes-etapa2.sql`.

## Usuários de teste

Todos com a senha **`123456`**.

| Id | E-mail | Perfil | Vem de |
|---|---|---|---|
| 1 | `admin@petraiz.dev` | ADMIN | migração (script 5) |
| 2 | `produtor@petraiz.dev` | PRODUCER (Sítio Raiz Orgânicos, aprovado) | migração (script 5) |
| 1001 | `contato@sitioboavista.dev` | PRODUCER (Sítio Boa Vista, aprovado) | `dados-catalogo-usuarios.sql` |
| 1002 | `contato@fazendaraizes.dev` | PRODUCER (Fazenda Raízes, aprovado) | `dados-catalogo-usuarios.sql` |

Os scripts não criam cliente: crie um pelo app ou com
`POST http://localhost:8081/api/v1/usuarios/clientes`
(`{"name":"Cliente Teste","email":"cliente@teste.dev","cpf":"<CPF válido>","password":"123456"}`).

Login: `POST http://localhost:8081/api/v1/auth` com `{"email": "...", "password": "123456"}` → `{"token": "..."}`.

## Testes

Cada serviço tem testes end-to-end (sobem o Spring numa porta aleatória e usam o banco `_test`).
Os outros serviços são trocados por stubs HTTP, então dá para testar um serviço sozinho:

```powershell
$env:DB_PASSWORD="<sua senha>"
cd service-usuarios; .\mvnw test
cd service-produtos; .\mvnw test
cd service-pedidos;  .\mvnw test
```

## Estrutura

```
pet-raiz-servicos/
├── README.md                 este arquivo
├── criar-bancos.sql
├── docs/API-CONTRATOS.md     contrato para o app Android
├── service-usuarios/         mesmo layout nos 3 serviços:
├── service-produtos/           src/main/kotlin/com/raizpet/api/{config,entity,exception,jwt,repository,
└── service-pedidos/            service,web/{controller,dto,exception}}, docs/db/, README.md
```

`client/` (chamadas HTTP a outros serviços) só existe em produtos e pedidos, que são os que chamam
outros; `util/` só no usuários (CPF/CNPJ).
