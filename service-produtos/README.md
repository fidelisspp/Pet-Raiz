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

`GET /images/<arquivo>` (público) procura em dois lugares, nesta ordem
(`spring.web.resources.static-locations=classpath:/static/,file:${petraiz.uploads.dir}/`):

1. `src/main/resources/static/images/`: fotos do seed e `sem-foto.png` (imagem padrão de produto sem foto,
   `IMAGEM_SEM_FOTO` em `ProdutoCreateDto.kt`). Arquivo novo aqui só aparece depois de reiniciar.
2. `<petraiz.uploads.dir>/images/`: fotos enviadas pelo produtor em `POST /produtos/meus/imagens`.
   Padrão `./uploads` (variável `UPLOADS_DIR`), relativo à pasta onde o serviço roda; no IntelliJ,
   o *Working directory* da Run Configuration (normalmente `service-produtos`). Fica fora do Git (`.gitignore`).
   Servido na hora, sem reiniciar.

O banco guarda só o caminho relativo (`/images/x.jpg`).

**Upload:** multipart, campo `arquivo`, até 2 MB (`spring.servlet.multipart.max-file-size`). O tipo
(jpg/png/webp) é decidido pelos primeiros bytes do arquivo, não pelo nome nem pelo Content-Type. O nome
gravado é um UUID. Foto enviada e nunca usada em produto continua na pasta (não há limpeza automática).

A regra de segurança de `/produtos/meus/**` (autenticado) vem **antes** do `permitAll` de `GET /produtos/*`:
o Spring Security usa a primeira regra que casar, e `/produtos/*` também casa com `/produtos/meus`.

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
| GET | `/produtos/admin` | ADMIN | Produtos publicados com `busca` (produto ou produtor) e `categoriaId` (Etapa 5.4). Depende do service-usuarios |
| GET | `/produtos/admin/resumo` | ADMIN | `{products, producers}` publicados (Etapa 5.4) |
| DELETE | `/produtos/admin/{id}` | ADMIN | Soft delete de qualquer produto (Etapa 5.4) |
| GET | `/categorias/resumo` | ADMIN | Categorias com `productCount` (ativos) e `removedProductCount` (Etapa 5.2) |
| POST, PUT `/{id}`, DELETE `/{id}` | `/categorias` | ADMIN | Criar, renomear (nome único sem maiúscula/acento → 409) e excluir (só sem produtos, nem removidos → 409) |
| POST | `/produtos` | PRODUCER | Cadastro; dono = claim `id`. `imageUrl` opcional = `/images/arquivo.(jpg\|jpeg\|png\|webp)`; sem ele grava `/images/sem-foto.png`. `petType` opcional (padrão `both`). Resposta traz `producerId` |
| POST | `/produtos/meus/imagens` | PRODUCER | Upload multipart (campo `arquivo`, jpg/jpeg/png/webp, até 2 MB) → 201 `{"url":"/images/<uuid>.jpg"}`. 422 com `errors.arquivo`; 415 se não for multipart |
| GET | `/produtos/meus` | PRODUCER | Produtos do dono do token, paginado (padrão `createdAt,desc`, 10). Inclui estoque 0, exclui removidos. Não chama o service-usuarios |
| GET | `/produtos/meus/{id}` | PRODUCER | Um produto do dono; de outro produtor, removido ou inexistente → 404 |
| PUT | `/produtos/meus/{id}` | PRODUCER | Mesmo corpo do POST. `imageUrl`/`petType` ausentes mantêm os atuais. 404 como acima; 422 |
| DELETE | `/produtos/meus/{id}` | PRODUCER | Soft delete (`active=false`): some do catálogo e do `/meus`. 204; 404 como acima |
| GET | `/interno/produtos?ids=1,2` | `X-Internal-Key` | Produtos existentes (ativos ou não), máx. 100 ids |
| POST | `/interno/estoque/reserva` | `X-Internal-Key` | `{"itens":[{"productId":1,"quantity":2}]}`. Tudo ou nada: 409 (estoque/inativo) ou 404 (inexistente) sem baixar nada |
| POST | `/interno/estoque/estorno` | `X-Internal-Key` | Mesma lista da reserva; devolve as quantidades. Sem idempotência (ver `../service-pedidos/README.md`) |
| GET | `/images/<arquivo>` | público | Imagens de produtos e produtores |
