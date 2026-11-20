# service-pedidos (porta 8082)

Carrinho, endereços e pedidos do Pet Raiz em serviços (ver `../README.md`). **Não emite token** e
**não tem tabela de usuário nem de produto**.
Banco: `pet_pedidos_db` (testes: `pet_pedidos_db_test`). Montagem e ordem dos scripts em `../README.md`.

## Rodar e testar

```bash
./mvnw spring-boot:run     # sobe em http://localhost:8082 (precisa do service-produtos na 8080 e do service-usuarios na 8081)
./mvnw clean test          # e2e contra pet_pedidos_db_test; os outros dois serviços são trocados por stubs HTTP
```

Swagger: http://localhost:8082/docs-petraiz.html

URLs dos outros serviços: `PRODUTOS_URL` (padrão `http://localhost:8080`) e `USUARIOS_URL`
(padrão `http://localhost:8081`). O checkout precisa da rota `GET /api/v1/interno/clientes/{id}`
do service-usuarios. Se o service-usuarios estiver numa versão sem essa rota, o checkout responde
**403 para todo mundo**, porque lá a rota dá 404 e aqui 404 quer dizer cliente que não existe.

## Token

É validado só pela assinatura (mesma `petraiz.jwt.secret-key`), pelo `exp` e pelas claims `id`, `sub` e
`role`. Token sem `id` inteiro, ou com `role` fora de CUSTOMER/PRODUCER/ADMIN, dá 401. Todas as rotas
abaixo exigem **CUSTOMER**; outro perfil recebe 403. Dado de outro cliente (endereço, pedido, item do
carrinho) responde **404**, como se não existisse.

## Endpoints (prefixo `/api/v1`; CUSTOMER, exceto `/pedidos/recebidos/**`, que é PRODUCER)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/enderecos` | Endereços do cliente, por id |
| GET | `/enderecos/{id}` | Um endereço |
| POST | `/enderecos` | `{label?, recipientName, street, number, complement?, neighborhood, city, state, zipCode}` → 201. `state` vira maiúsculo, `zipCode` vira `00000-000` |
| PUT | `/enderecos/{id}` | Troca todos os campos. **409** se o endereço já foi usado em pedido (o pedido guarda só o `address_id`; alterar mudaria o endereço de uma compra já feita) |
| DELETE | `/enderecos/{id}` | 204; **409** se o endereço já foi usado em pedido (FK `fk_purchase_order_address`) |
| GET | `/carrinho` | Criado vazio no 1º acesso. Itens com `name, price, stock, imageUrl, producerId` do service-produtos, `quantity`, `subtotal`, `available`; `totalQuantity` e `subtotal` (sem frete) |
| POST | `/carrinho/itens` | `{productId, quantity}`. Já no carrinho → **soma**. Passou do estoque → 409 (`errors = {productId: motivo}`); inativo → 409; inexistente → 404 |
| PATCH | `/carrinho/itens/{productId}` | `{quantity}` = valor novo (não soma). 409 acima do estoque, 404 fora do carrinho |
| DELETE | `/carrinho/itens/{productId}` | 204 (não chama o service-produtos) |
| POST | `/pedidos` | Checkout do carrinho inteiro: `{addressId, shippingMethod: standard\|express, paymentMethod: pix\|card}` → 201 com o pedido completo |
| POST | `/pedidos/{id}/pagamento` | Pagamento simulado: `pending` → `paid` + `confirmedAt`. Se já não está pending → 409 |
| GET | `/pedidos` | Histórico paginado (`page`, `size`), mais recente primeiro |
| GET | `/pedidos/{id}` | Pedido completo (só o dono) |
| GET | `/favoritos` | Favoritos do cliente, mais recentes primeiro, com os dados do produto (uma chamada a `/interno/produtos?ids=`). Produto removido → `available=false`. 503 se o service-produtos cair |
| POST | `/favoritos` | `{productId}`. **Idempotente**: 201 criado, 200 se já era favorito. Inexistente → 404; inativo → 409. Corrida entre dois POST iguais: a `uk_favorite` barra o segundo, que responde 200 |
| DELETE | `/favoritos/{productId}` | 204, mesmo se não era favorito. Não chama o service-produtos |
| GET | `/pedidos/recebidos` | **PRODUCER.** As partes (`order_producer_group`) do produtor do token, só de pedidos **pagos**, mais recentes primeiro. Filtro `status` opcional; `page`, `size` |
| GET | `/pedidos/recebidos/{grupoId}` | **PRODUCER.** Uma parte; de outro produtor, não paga ou inexistente → 404 |
| PATCH | `/pedidos/recebidos/{grupoId}/status` | **PRODUCER.** `{status}`: só o próximo da sequência `pending → preparing → shipped → delivered`; voltar/pular/repetir → 409. `SELECT ... FOR UPDATE` no grupo evita que dois cliques simultâneos avancem duas etapas |

Formato do pedido recebido: `id (grupoId), orderId, createdAt, paidAt, status, nextStatus, shippingMethod,
recipientName, address{...}, items[...], total` (`total` = só os itens do produtor, sem frete).
As rotas de produtor ficam num controller separado (`PedidoRecebidoController`); `/pedidos/recebidos` é um caminho
literal, então o Spring o escolhe antes de `/pedidos/{id}` (rota do cliente).

Formato do pedido: `id, createdAt, address{...}, shippingMethod, shippingAmount, itemsAmount, totalAmount,
payment{method, status, amount, createdAt, confirmedAt}, producers[{producerId, deliveryStatus, items[{productId,
productName, productImageUrl, quantity, unitPrice, subtotal}], subtotal}]`.
Frete por pedido: standard 12.90, express 24.90. `totalAmount = itemsAmount + shippingAmount = payment.amount`.
Na API o cartão é `card`; no banco, `payment_method = 'credit_card'`.

## Erros

Todos os erros vêm no formato `ErrorMessage`: 400 (JSON malformado ou id não numérico), 401, 403, 404,
409, 422 (campos inválidos ou carrinho vazio), 500 (pedido não gravado depois da reserva; o estoque
foi estornado) e **503**. O 503 sai quando o service-produtos ou o service-usuarios está fora do ar,
dá timeout, responde 5xx/401 ou manda JSON inesperado. Endereços, pedidos e carrinho vazio continuam
funcionando sem os outros serviços.

## Dados de outros serviços

- O cliente é o `id` do token (perfil CUSTOMER). Não existe tabela de cliente aqui: `cart.customer_id`,
  `address.customer_id` e `purchase_order.customer_id` são INT simples, sem FK.
- Produtos (nome, preço, estoque, producerId) vêm do service-produtos pelas rotas internas, com o
  header `X-Internal-Key`.
- No checkout, antes de reservar, o serviço confere no service-usuarios
  (`GET /api/v1/interno/clientes/{id}` → `{id, suspended}`) se o cliente não está suspenso.
  Suspenso, ou id que não é de cliente lá (404): **403**.
- Preço, nome e imagem copiados no pedido são os que a **resposta da reserva** devolve, e o
  `producerId` de cada item define o grupo do pedido.
- O nome do destinatário fica no endereço (`address.recipient_name`), e o nome e a imagem do produto
  são copiados para o item na hora da compra (`order_item.product_name`, `product_image_url`).
- `favorite` existe no banco, mas não tem endpoints nesta fase.

## Compensação do estoque (SIMPLIFICAÇÃO ACEITA)

Não existe transação distribuída entre os serviços. Antes de qualquer alteração, o checkout confere,
nesta ordem: endereço do cliente (404), carrinho não vazio (422) e cliente não suspenso (403/503).
Depois disso (`service/CheckoutService.kt`):

1. **Reserva:** chama o service-produtos, que dá baixa no estoque de todos os itens numa
   transação local dele (ou todos, ou nenhum; sem estoque → 409 e o pedido nem começa).
2. **Pedido:** numa transação local daqui (`PedidoTransacao`), cria `purchase_order`, os grupos
   por produtor, os itens e o `payment` (`pending`), e esvazia o carrinho. Só apaga os itens que
   entraram no pedido: um item adicionado durante o checkout fica no carrinho.
3. **Se o passo 2 falhar**, chama `POST /api/v1/interno/estoque/estorno` no service-produtos com
   **a mesma lista de itens** `{productId, quantity}`, que devolve as quantidades ao estoque.
4. **Se o estorno também falhar**, só é registrado um **log de erro** (com os itens) para correção
   manual: procure por `ESTORNO FALHOU - CORRIGIR MANUALMENTE`.

Nos casos 3 e 4 a resposta é 500 e o carrinho continua igual. Os testes estão em `CompensacaoIT`.

O que esta versão simplificada **não** cobre, de propósito:

- **Sem idempotência:** não há `checkout_id` nem tabela de movimentos de estoque. Se o estorno
  for reenviado por engano, o estoque é devolvido duas vezes.
- **Timeout na reserva:** se a chamada do passo 1 der timeout depois de o service-produtos já ter
  dado baixa, este serviço não tem como saber. O estoque fica baixado sem pedido até alguém
  corrigir pelo log.
- **Estorno com falha** não é repetido automaticamente.
- **Cancelamento de pedido** (e a devolução de estoque que viria com ele) ficou para depois.
- **Estoque do carrinho não é reservado:** adicionar ou alterar só confere o estoque do momento.
  Quem garante o estoque é a reserva no checkout.
- **Produtor não é conferido** no checkout: só o produto (ativo e com estoque). O catálogo já
  esconde produto de produtor não aprovado.
