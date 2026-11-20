# Pet Raiz em serviços

O back-end do Pet Raiz dividido em 3 serviços Spring Boot independentes (disciplina de
Arquitetura Orientada a Serviços). O projeto original `Documents/pet-raiz` fica intacto como
referência; quando precisar rodá-lo junto, suba-o na porta 8090
(`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090`).

| Serviço | Porta | Banco | Responsável por |
|---|---|---|---|
| `service-usuarios` | 8081 | `pet_usuarios_db` | login (emite o JWT), usuários, `/me`, produtores públicos |
| `service-produtos` | 8080 | `pet_produtos_db` | produtos, categorias, imagens em `/images/**` |
| `service-pedidos` | 8082 | `pet_pedidos_db` | carrinho, endereços, pedidos |

Cada banco tem também um `_test`, usado pelo `./mvnw test` do serviço.

## Regras

- Um banco por serviço. Nenhum serviço lê tabela de outro: o que era FK entre serviços virou um
  `INT` simples (ex.: `product.producer_id`).
- Só o `service-usuarios` emite token. Os outros validam com o mesmo segredo (HS256) e leem
  `id` e `role` das claims.
- Chamadas entre serviços: HTTP síncrono, URL base no `application.properties`, rotas
  `/api/v1/interno/**` protegidas pelo header `X-Internal-Key`.
- Imagens: o banco guarda só o caminho relativo (`/images/x.jpg`); o app junta com a URL base
  do `service-produtos`.

## Variáveis de ambiente

A senha do MySQL não fica no código: defina `DB_PASSWORD` antes de subir ou testar cada serviço
(no IntelliJ: Run Configuration → Environment variables → `DB_PASSWORD=<sua senha>`; no terminal:
`$env:DB_PASSWORD="<sua senha>"; .\mvnw test`). Sem ela o serviço não conecta no banco.
Opcionais (têm padrão de dev): `DB_USERNAME` (root), `JWT_SECRET`, `INTERNAL_KEY`, `USUARIOS_URL`, `PRODUTOS_URL`,
`UPLOADS_DIR` (service-produtos; pasta das fotos enviadas, padrão `./uploads`).

## Montar os bancos (ordem importa)

No PowerShell, cada script é enviado assim
(troque `<arquivo>` e `<banco>`; o `mysql` fica em `C:\Program Files\MySQL\MySQL Server 8.0\bin`):

```powershell
Get-Content <arquivo> -Raw -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4 <banco>
```

| # | Script | Banco(s) |
|---|---|---|
| 1 | `criar-bancos.sql` | (nenhum) |
| 2 | `service-usuarios/docs/db/schema-usuarios.sql` + `alteracoes-etapa2.sql` | `pet_usuarios_db` e `pet_usuarios_db_test` |
| 3 | `service-produtos/docs/db/schema-produtos.sql` + `alteracoes-etapa2.sql` | `pet_produtos_db` e `pet_produtos_db_test` |
| 4 | `service-pedidos/docs/db/schema-pedidos.sql` + `alteracoes-etapa2.sql` | `pet_pedidos_db` e `pet_pedidos_db_test` |
| 5 | `service-usuarios/docs/db/migracao-de-pet-raiz.sql` | (nomes qualificados; lê `pet_raizdb`) |
| 6 | `service-produtos/docs/db/migracao-de-pet-raiz.sql` | idem |
| 7 | `service-pedidos/docs/db/migracao-de-pet-raiz.sql` | idem |
| 8 | `service-usuarios/docs/db/dados-catalogo-usuarios.sql` | `pet_usuarios_db` |
| 9 | `service-produtos/docs/db/dados-catalogo-produtos.sql` | `pet_produtos_db` |

As migrações mantêm os IDs do `pet_raizdb` (é isso que mantém `product.producer_id` válido sem FK)
e só fazem `SELECT` nele. Os bancos `_test` recebem só schema + alterações: os dados de teste
vêm do `@Sql` de cada serviço.
