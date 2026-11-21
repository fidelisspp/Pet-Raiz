# CLAUDE.md — Back-end do Raiz Pet (pet-raiz-servicos)

> Este arquivo fica em `C:\Users\Tech2e\Documents\pet-raiz-servicos\CLAUDE.md`.
> Leia-o inteiro no início de toda sessão. Ele é a fonte da verdade sobre o projeto.

## 1. Quem sou eu e como trabalhar comigo

Sou o João, estudante de Sistemas para Internet (Senac). Este é o projeto final da disciplina de
**Programação Orientada a Serviços (SOA)**. Prazo da entrega final: **30/11 ou 02/12/2026**.

Quero terminar o projeto com você, mas também **entender** o que está sendo feito. Por isso:

- Responda sempre em **português**, de forma direta.
- Trabalhe **por etapas** (seção 7). Ao fim de cada etapa: testes, relatório e **PARE** para eu aprovar.
- Antes de implementar uma etapa, mostre um **plano curto** (arquivos que vai criar/alterar e por quê).
- No relatório, explique em poucas linhas **o conceito** de cada coisa nova (ex.: o que é multipart,
  por que a regra de segurança precisa vir antes do permitAll). Sem textão.
- Se algo que eu pedir fugir do escopo ou for consumir muito tempo, me avise antes.
- Se precisar de uma decisão minha, pergunte. Não invente regra de negócio.

## 2. Requisitos da disciplina (não negociáveis)

- Back-end **somente em Kotlin + Spring Boot**. Só devolve **JSON** (sem Thymeleaf/HTML).
- O front é um app Android nativo em XML (outro projeto: `C:\Users\Tech2e\AndroidStudioProjects\PetRaiz`).
- A arquitetura **é o assunto da disciplina**: os serviços precisam continuar independentes.

## 3. Arquitetura (SOA)

"Um app, três serviços, três bancos, um token."

| Serviço | Porta | Banco (dev / teste) | Responsável por |
|---|---|---|---|
| `service-usuarios` | 8081 | `pet_usuarios_db` / `_test` | login (emite o JWT), clientes, produtores, admin, `/me`, produtores públicos |
| `service-produtos` | 8080 | `pet_produtos_db` / `_test` | produtos, categorias, estoque, imagens (`/images/**`) |
| `service-pedidos`  | 8082 | `pet_pedidos_db` / `_test`  | carrinho, endereços, pedidos, pagamento simulado, favoritos |

Regras que NÃO podem ser quebradas:

1. **Um banco por serviço.** Nenhum serviço lê tabela de outro. Referência entre serviços é **ID simples**
   (sem FK, sem relação JPA). Ex.: `product.producer_id`, `cart.customer_id`.
2. **JWT (HS256, segredo compartilhado):** só o `service-usuarios` emite. Claims: `sub` (e-mail), `id`, `role`
   (CUSTOMER, PRODUCER, ADMIN). Os outros serviços validam **só pelas claims** (`JwtUsuario`), sem banco de usuários.
   Expira em 8 h.
3. **Chamadas entre serviços:** HTTP síncrono com `RestClient`, URL base no `application.properties`.
   Rotas internas ficam em `/api/v1/interno/**` e exigem o header **`X-Internal-Key`**
   (padrão de dev: `troque-esta-chave-interna-petraiz`; produção: variável `INTERNAL_KEY`). O app nunca usa essas rotas.
4. **Outro serviço fora do ar → 503** no formato `ErrorMessage`. Nunca derrubar o serviço que chamou.
5. Erros sempre no formato `ErrorMessage` (`message` e, no 422, `errors` campo → mensagem), via `ApiExceptionHandler`.
6. **Regras de segurança:** rotas autenticadas específicas (ex.: `/produtos/meus/**`, `/produtores/me`)
   precisam vir **ANTES** dos `permitAll` mais genéricos (ex.: `GET /produtos/**`, `GET /produtores/**`).
7. Mesma estrutura de pacotes nos três serviços (siga o que já existe).
8. Swagger em `/docs-petraiz.html` em cada serviço — documente rotas novas.

Comunicação que já existe (é o que mostro ao professor):
- Catálogo (produtos → usuários): lista de produtores aprovados, uma chamada por requisição.
- Checkout (pedidos → usuários → produtos): **confere** se o cliente está suspenso, **reserva** o estoque
  (tudo ou nada, `SELECT ... FOR UPDATE`), **cria** o pedido numa transação local e, se falhar, **devolve**
  o estoque (compensação simplificada, documentada no README do service-pedidos).

## 4. Contratos que o app já usa (NÃO quebrar)

Se uma mudança obrigar a alterar algum destes, PARE e me pergunte antes.

service-usuarios (8081):
- `POST /api/v1/auth` `{email, password}` → `{token}`. Senha errada → 400.
- `GET /api/v1/usuarios/me` → `{id, name, email, phone, role, createdAt}`.
- `POST /api/v1/usuarios/clientes` `{name, email, cpf, password, phone?}` → 201. E-mail/CPF repetido → 409; inválido → 422.
- `POST /api/v1/usuarios/produtores`: JSON **ou** multipart (`dados` + `documento`, Etapa 5.1). O app usa o multipart.
- `GET /api/v1/produtores`, `GET /api/v1/produtores/{id}` (públicos, só aprovados).
- 403 do login de produtor: `"Seu cadastro está em análise, aguarde aprovação"` e
  `"Cadastro de produtor rejeitado. Motivo: …"`. **O app reconhece essas frases pelo início: não mude sem avisar.**
- Internas: `GET /interno/produtores/aprovados`, `GET /interno/clientes/{id}`.

service-produtos (8080):
- `GET /api/v1/produtos` (filtros `nome`, `categoriaId`, `produtorId`, `petType`, `page`, `size`, `sort`) → página
  `{content, totalElements, ...}`. `petType=dog` traz dog+both; `cat` traz cat+both.
- `GET /api/v1/produtos/{id}`, `GET /api/v1/categorias`.
- `POST /api/v1/produtos` (PRODUCER). `imageUrl` é **caminho relativo** `/images/arquivo.(jpg|jpeg|png|webp)`.
- Imagens: `GET /images/**` (público).
- Internas: `GET /interno/produtos?ids=`, reserva de estoque, estorno.

service-pedidos (8082), todas CUSTOMER:
- Endereços: CRUD em `/api/v1/enderecos` (com `recipientName`).
- Carrinho: `GET /api/v1/carrinho`, `POST /carrinho/itens` (soma), `PATCH /carrinho/itens/{productId}` (troca), `DELETE`.
- Pedidos: `POST /api/v1/pedidos {addressId, shippingMethod: standard|express, paymentMethod: pix|card}`,
  `POST /pedidos/{id}/pagamento`, `GET /pedidos` (paginado), `GET /pedidos/{id}`.

Mantenha também um arquivo **`docs/API-CONTRATOS.md`** na raiz desta pasta com todas as rotas (método, URL,
perfil, corpo, resposta, erros). **Atualize-o em toda etapa.** O agente do Android Studio lê esse arquivo para
montar as telas.

## 5. Decisões já tomadas

- Frete por pedido: standard 12,90; express 24,90. Pagamento simulado em dois passos (sem Stripe).
- Status do pedido (por produtor): `pending → preparing → shipped → delivered`.
- Produto tem `pet_type` (dog/cat/both). Ingredientes e validade **não existem** (até eu decidir o contrário).
- Imagens: o banco guarda **só o caminho relativo**; o app junta com a URL do service-produtos.
- Produtor tem `image_url`, `city`, `state`. Fotos dos produtores também são servidas pelo service-produtos.
- Endereço padrão: sem coluna; o checkout usa o primeiro.
- Remover produto = soft delete (`active=false`). "Rascunho" não existe.
- Aprovação de produtor (Etapa 5.1): `pending → approved | rejected` e `rejected → pending` (reabrir); o resto é 409.
  Rejeitar exige motivo (`reason`), que o produtor vê no 403 do login. Aprovado não muda mais.
- Documento do cadastro: **qualquer formato**, até **5 MB**, em `./uploads/documentos/` (não é estático: só o ADMIN baixa).
- Telas do admin sem base no back-end ficam **fora**: "cobrar produtor" (não há notificações), "reembolso"
  (pagamento é simulado) e "remover cliente" (vira suspender/reativar).

## 6. Ambiente na minha máquina

- Pasta: `C:\Users\Tech2e\Documents\pet-raiz-servicos\` (um subprojeto Maven por serviço, cada um com `mvnw`).
- MySQL 8 local. Scripts de cada serviço em `<serviço>/docs/db/` (schema, migração, seeds, alterações).
  Mudou tabela? Gere o `ALTER TABLE` num script novo em `docs/db/` e atualize os seeds para continuar reproduzível.
- Usuários de teste (senha `123456`): admin id 1 (`admin@petraiz.dev`), produtor id 2 (`produtor@petraiz.dev`),
  produtores 1001 e 1002 (e-mails no `dados-catalogo-usuarios.sql`).
- Ordem para subir: usuários (8081) → produtos (8080) → pedidos (8082).
- **`Documents\pet-raiz` é o monolito ANTIGO.** Não altere e não suba na 8080 (no console ele aparece como `[pet-raiz-api]`).
- Eu deixo os serviços rodando em janelas do IntelliJ. **Não derrube meus processos.** Para testar uma versão
  alterada, suba uma instância temporária em outra porta (8083–8089), teste e desligue. No relatório, me diga
  quais serviços eu preciso reiniciar.
- Cuidado conhecido: escrever `/**` dentro de um comentário KDoc `/** ... */` quebra a compilação em Kotlin.
- Pouca memória (emulador + serviços abertos): o daemon do compilador Kotlin pode não subir
  ("Failed connecting to the daemon"). Use `./mvnw test -Dkotlin.compiler.daemon=false`.
- Dados de teste criados por você: limpe no final (ou me diga o que ficou).

## 7. O que falta (roteiro até a entrega)

Antes de qualquer etapa: rode `git status` e `git log --oneline -5` e veja o que já foi feito. Algumas etapas
podem estar parcialmente prontas.

**Etapa 0 — Git.** Repositório na raiz desta pasta, `.gitignore` (target/, .idea/, *.iml, uploads/), sem senha
real versionada (use variável de ambiente com padrão de dev). Commit "Estado da apresentação de 11/11".

**Etapa 1 — Produtor gerencia os próprios produtos.**
- usuários: `GET /api/v1/produtores/me` (PRODUCER) → `{id, companyName, shortDescription, imageUrl, city, state, status}`.
- produtos: `GET /api/v1/produtos/meus?page=&size=` (inclui estoque 0; não inclui removidos);
  `GET`, `PUT`, `DELETE /api/v1/produtos/meus/{id}` (só o dono; de outro → 404; DELETE é soft delete).
  `imageUrl` passa a ser opcional no POST/PUT.

**Etapa 2 — Upload de foto do produto.**
- produtos: `POST /api/v1/produtos/meus/imagens` (PRODUCER, multipart, campo `arquivo`) → `{url: "/images/<uuid>.jpg"}`.
  Aceitar jpg/jpeg/png/webp até 2 MB; nome com UUID. Salvar numa **pasta externa** (ex.: `./uploads/images/`),
  nunca em `src/main/resources` (não recebe arquivos com o servidor rodando). Servir na mesma URL `/images/**`
  com `spring.web.resources.static-locations=classpath:/static/,file:./uploads/`.

**Etapa 3 — Pedidos recebidos pelo produtor.**
- pedidos: `GET /api/v1/pedidos/recebidos?status=&page=&size=` (PRODUCER): os `order_producer_group` do produtor
  do token, só de pedidos **pagos**, com itens, total da parte dele, nome do destinatário e endereço.
  `GET /pedidos/recebidos/{grupoId}`, `PATCH /pedidos/recebidos/{grupoId}/status {status}` — só avança uma
  etapa por vez (pending→preparing→shipped→delivered); voltar ou pular → 409.
- O `GET /pedidos` do cliente passa a refletir o status de cada parte.

**Etapa 4 — Favoritos** (no service-pedidos; tabela `favorite` já existe).
- `GET /api/v1/favoritos` (dados do produto via `/interno/produtos?ids=`), `POST /favoritos {productId}`
  (idempotente), `DELETE /favoritos/{productId}`. CUSTOMER.

**Etapa 5 — Acabamento.**
- Endereço já usado em pedido: bloquear também o PUT (409), igual ao DELETE.
- Revisar Swagger dos 3 serviços e o `docs/API-CONTRATOS.md`.
- README da raiz com: arquitetura, como subir, ordem dos scripts, usuários de teste.

**Etapa 5.1 — Aprovação de produtor** (usuários). *Feita.*
- `GET /api/v1/produtores/pendentes` e `GET /produtores/cadastros?status=` (ADMIN, paginados), `PATCH /produtores/{id}/status`
  `{status, reason?}` (ADMIN), `GET /produtores/{id}/documento` (ADMIN).
- `POST /usuarios/produtores` aceita multipart com o documento (tabela `producer_document`, que já existia).
- Script `service-usuarios/docs/db/alteracoes-etapa5-1.sql`.

**Etapas 5.2 a 5.6 — Telas do admin** (uma por vez, cada uma com plano, testes e relatório):
- **5.2 Categorias** (produtos): *Feita.* `GET /categorias/resumo`, `POST`, `PUT`, `DELETE /api/v1/categorias` (ADMIN).
  Nome único sem maiúscula/acento; excluir só sem nenhum produto (nem removido, por causa da FK) → senão 409.
- **5.3 Clientes**: *Feita.* usuários: `GET /clientes` com `busca` e `suspenso`, `PATCH /clientes/{id}/suspensao`;
  pedidos: `GET /pedidos/admin/clientes?ids=` (pedidos pagos e total gasto). O **app** junta as duas respostas
  (usuários não chama pedidos: seria um ciclo). Rotas de admin do service-pedidos ficam em `PedidoAdminController`.
- **5.4 Produtos** (produtos): *Feita.* `GET /produtos/admin` (publicados = recorte do catálogo; busca no nome do
  produto ou do produtor), `GET /produtos/admin/resumo`, `DELETE /produtos/admin/{id}` (soft delete), em `ProdutoAdminController`.
- **5.5 Pedidos** (pedidos): *Feita.* `GET /pedidos/admin` (todos, inclusive não pagos; busca por número ou nome do
  cliente via `GET /interno/clientes?ids=|busca=` do service-usuarios, com degradação), `GET /pedidos/admin/resumo`,
  `POST /pedidos/admin/{id}/cancelamento` (só sem parte enviada; partes `canceled`, estorno, `paid` → `refunded`) e
  `PATCH /pedidos/admin/grupos/{grupoId}/status` (correção livre entre os 4). `delivery_status` ganhou `canceled` e
  `purchase_order` ganhou `canceled_at` (`alteracoes-etapa5-5.sql`). Atrasado = pago há mais de 3 dias com parte não enviada.
- **5.6 Início do admin**: *Feita* (só no app, sem rota nova): os quatro números vêm de `/pedidos/admin/resumo`,
  `/clientes?size=1` (`totalElements`) e `/produtos/admin/resumo`.

**Etapa 6 — A confirmar comigo** (não comece sem eu pedir): recuperação de senha, avaliações de produto
(possível `service-avaliacoes` na porta 8084), ingredientes.

## 8. Fluxo de cada etapa

1. Levantamento: o que já existe no código, no banco e no git.
2. Plano curto (arquivos e motivo). Espere meu "ok" se houver decisão de negócio em aberto; se não houver, siga.
3. Implementação seguindo os padrões existentes (copie o estilo dos controllers/serviços que já existem).
4. `./mvnw test` em cada serviço alterado + roteiro curl com os serviços reais (portas temporárias se preciso).
5. Atualize `docs/API-CONTRATOS.md`, scripts SQL e READMEs.
6. Relatório (seção 9) e **PARE**.
7. Depois do meu "aprovado": commit com mensagem descritiva (ex.: "Etapa 1: produtor gerencia produtos").
   Não faça push sem eu pedir.

## 9. Formato do relatório

- **O que mudou:** arquivos, em lista curta, com o motivo.
- **Conceito novo:** 2 a 5 linhas explicando o que eu deveria aprender desta etapa.
- **Testes:** resultado do `mvnw test` e tabela dos curl (chamada → status → resultado).
- **Contrato para o app:** as rotas novas/alteradas, prontas para eu passar ao agente do Android Studio.
- **Ação minha:** o que preciso reiniciar, rodar no banco ou decidir.
