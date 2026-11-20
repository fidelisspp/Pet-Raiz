package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.PedidoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.function.Consumer

/**
 * Checkout, pagamento simulado e histórico.
 * Seed: endereços 500/501 (Bia), 502 (Bob); pedido 700 do Bob (pago), 701 da Bia (pendente);
 * carrinho 600 do Bob com 1x produto 100. UsuariosStub: Bia ativa, Bob suspenso, outros = 404.
 */
class PedidoIT : IntegracaoBase() {

    private fun checkout(quem: Consumer<HttpHeaders>, corpo: String): RestTestClient.ResponseSpec =
        post("/api/v1/pedidos", quem, corpo)

    private fun criado(spec: RestTestClient.ResponseSpec): PedidoResponseDto =
        spec.expectStatus().isCreated.expectBody(PedidoResponseDto::class.java).returnResult().responseBody!!

    private fun pedido(id: Int, quem: Consumer<HttpHeaders> = Tokens.clienteBia()): PedidoResponseDto =
        testClient.get().uri("/api/v1/pedidos/$id").headers(quem).exchange()
            .expectStatus().isOk.expectBody(PedidoResponseDto::class.java).returnResult().responseBody!!

    private fun montarCarrinhoDaBia() {
        adicionar(Tokens.clienteBia(), 100, 2).expectStatus().isOk   // produtor 103
        adicionar(Tokens.clienteBia(), 101, 3).expectStatus().isOk   // produtor 103
        adicionar(Tokens.clienteBia(), 106, 1).expectStatus().isOk   // produtor 106
    }

    // ---------- POST /pedidos: sucesso ----------

    @Test
    fun checkout_ComEstoque_CriarPedidoAgrupadoPorProdutorReservarEEsvaziarCarrinho() {
        montarCarrinhoDaBia()

        val p = criado(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"express","paymentMethod":"card"}"""))

        assertThat(p.address.id).isEqualTo(500)
        assertThat(p.address.recipientName).isEqualTo("Bia Souza")
        assertThat(p.shippingMethod).isEqualTo("express")
        assertThat(p.shippingAmount).isEqualByComparingTo("24.90")
        // 2 x 89.90 + 3 x 19.50 + 1 x 30.00 = 268.30; + frete 24.90 = 293.20
        assertThat(p.itemsAmount).isEqualByComparingTo("268.30")
        assertThat(p.totalAmount).isEqualByComparingTo("293.20")
        assertThat(p.payment.method).isEqualTo("card")
        assertThat(p.payment.status).isEqualTo("pending")
        assertThat(p.payment.amount).isEqualByComparingTo("293.20")
        assertThat(p.payment.confirmedAt).isNull()

        assertThat(p.producers.map { it.producerId }).containsExactly(103, 106)
        val g103 = p.producers[0]
        assertThat(g103.deliveryStatus).isEqualTo("pending")
        assertThat(g103.subtotal).isEqualByComparingTo("238.30")
        assertThat(g103.items.map { it.productId to it.quantity }).containsExactly(100 to 2, 101 to 3)
        val item100 = g103.items[0]
        assertThat(item100.productName).isEqualTo("Ração Orgânica de Frango")
        assertThat(item100.productImageUrl).isEqualTo("/images/frango.jpg")
        assertThat(item100.unitPrice).isEqualByComparingTo("89.90")
        assertThat(item100.subtotal).isEqualByComparingTo("179.80")
        assertThat(p.producers[1].items.single().productName).isEqualTo("Mel para Cães")

        // Reserva com a lista do carrinho; estoque baixado; nenhum estorno
        assertThat(ProdutosStub.reservas.single()).containsExactly(100 to 2, 101 to 3, 106 to 1)
        assertThat(ProdutosStub.estoque).isEqualTo(mapOf(100 to 8, 101 to 27, 102 to 0, 103 to 5, 106 to 4))
        assertThat(ProdutosStub.estornos).isEmpty()
        assertThat(UsuariosStub.chamadas.get()).isEqualTo(1)

        // Carrinho vazio (o carrinho continua existindo)
        assertThat(carrinho(Tokens.clienteBia()).items).isEmpty()

        // Gravado no banco: 1 pedido, 2 grupos, 3 itens, 1 pagamento
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_producer_group WHERE purchase_order_id = ?", Int::class.java, p.id)).isEqualTo(2)
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM order_item i JOIN order_producer_group g ON g.id = i.order_producer_group_id WHERE g.purchase_order_id = ?",
            Int::class.java, p.id
        )).isEqualTo(3)
        assertThat(jdbc.queryForObject("SELECT payment_method FROM payment WHERE purchase_order_id = ?", String::class.java, p.id)).isEqualTo("credit_card")
        assertThat(pedido(p.id)).isEqualTo(p)
    }

    @Test
    fun checkout_StandardEPix_FreteDe12e90() {
        adicionar(Tokens.clienteBia(), 106, 2).expectStatus().isOk
        val p = criado(checkout(Tokens.clienteBia(), """{"addressId":501,"shippingMethod":"standard","paymentMethod":"pix"}"""))
        assertThat(p.shippingAmount).isEqualByComparingTo("12.90")
        assertThat(p.totalAmount).isEqualByComparingTo("72.90")
        assertThat(p.payment.method).isEqualTo("pix")
    }

    @Test
    fun checkout_PrecoNomeEImagemSaoCopiados_HistoricoNaoMudaSeOProdutoMudar() {
        adicionar(Tokens.clienteBia(), 100, 1).expectStatus().isOk
        val p = criado(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""))

        ProdutosStub.produtos.getValue(100).apply { name = "Nome Novo"; price = java.math.BigDecimal("150.00") }
        val item = pedido(p.id).producers.single().items.single()
        assertThat(item.productName).isEqualTo("Ração Orgânica de Frango")
        assertThat(item.unitPrice).isEqualByComparingTo("89.90")
    }

    // ---------- POST /pedidos: recusas ----------

    @Test
    fun checkout_SemEstoque_RetornarErrorMessageStatus409ECarrinhoIntacto() {
        montarCarrinhoDaBia()
        // Outra compra levou o estoque do 101 depois que a Bia montou o carrinho
        ProdutosStub.estoque[101] = 2

        val erro = esperarErro(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""), 409)
        assertThat(erro.errors).containsOnlyKeys("101")
        assertThat(erro.errors!!["101"]).contains("disponível 2")

        // Nada mudou: carrinho, estoque (tudo ou nada no service-produtos), pedidos; e nada a estornar
        assertThat(carrinho(Tokens.clienteBia()).items.map { it.productId to it.quantity }).containsExactly(100 to 2, 101 to 3, 106 to 1)
        assertThat(ProdutosStub.estoque).isEqualTo(mapOf(100 to 10, 101 to 2, 102 to 0, 103 to 5, 106 to 5))
        assertThat(ProdutosStub.estornos).isEmpty()
        assertThat(contar("purchase_order")).isEqualTo(2)
    }

    @Test
    fun checkout_ComProdutoQueFicouInativoOuSumiu_RetornarErrorMessageStatus409() {
        adicionar(Tokens.clienteBia(), 100, 1).expectStatus().isOk
        ProdutosStub.produtos.getValue(100).active = false
        val inativo = esperarErro(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""), 409)
        assertThat(inativo.errors!!["100"]).isEqualTo("produto inativo")

        ProdutosStub.produtos.remove(100)
        val sumiu = esperarErro(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""), 409)
        assertThat(sumiu.message).contains("100")
        assertThat(carrinho(Tokens.clienteBia()).items).hasSize(1)
        assertThat(contar("purchase_order")).isEqualTo(2)
    }

    @Test
    fun checkout_ClienteSuspenso_RetornarErrorMessageStatus403SemReservar() {
        val erro = esperarErro(checkout(Tokens.clienteBobSuspenso(), """{"addressId":502,"shippingMethod":"standard","paymentMethod":"pix"}"""), 403)
        assertThat(erro.message).contains("suspensa")
        assertThat(UsuariosStub.chamadas.get()).isEqualTo(1)
        assertThat(ProdutosStub.reservas).isEmpty()
        assertThat(carrinho(Tokens.clienteBobSuspenso()).items).hasSize(1)
        assertThat(contar("purchase_order")).isEqualTo(2)
    }

    @Test
    fun checkout_ClienteQueNaoExisteNoServicoDeUsuarios_RetornarErrorMessageStatus403() {
        val fantasma = Tokens.clienteInexistente()
        val endereco = post("/api/v1/enderecos", fantasma,
            """{"recipientName":"F","street":"R","number":"1","neighborhood":"B","city":"C","state":"SP","zipCode":"01000-000"}""")
            .expectStatus().isCreated.expectBody(Map::class.java).returnResult().responseBody!!["id"]
        adicionar(fantasma, 100, 1).expectStatus().isOk

        esperarErro(checkout(fantasma, """{"addressId":$endereco,"shippingMethod":"standard","paymentMethod":"pix"}"""), 403)
        assertThat(ProdutosStub.reservas).isEmpty()
    }

    @Test
    fun checkout_CarrinhoVazio_RetornarErrorMessageStatus422SemChamarOutrosServicos() {
        val erro = esperarErro(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""), 422)
        assertThat(erro.message).contains("vazio")
        assertThat(UsuariosStub.chamadas.get()).isZero()
        assertThat(ProdutosStub.reservas).isEmpty()
    }

    @Test
    fun checkout_EnderecoDeOutroClienteOuInexistente_RetornarErrorMessageStatus404() {
        adicionar(Tokens.clienteBia(), 100, 1).expectStatus().isOk
        esperarErro(checkout(Tokens.clienteBia(), """{"addressId":502,"shippingMethod":"standard","paymentMethod":"pix"}"""), 404)
        esperarErro(checkout(Tokens.clienteBia(), """{"addressId":9999,"shippingMethod":"standard","paymentMethod":"pix"}"""), 404)
        assertThat(ProdutosStub.reservas).isEmpty()
    }

    @Test
    fun checkout_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        adicionar(Tokens.clienteBia(), 100, 1).expectStatus().isOk
        val erro = esperarErro(checkout(Tokens.clienteBia(), """{"shippingMethod":"drone","paymentMethod":"boleto"}"""), 422)
        assertThat(erro.errors).containsOnlyKeys("addressId", "shippingMethod", "paymentMethod")
        // No banco é credit_card, mas a API aceita só pix | card
        esperarErro(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"credit_card"}"""), 422)
        esperarErro(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"STANDARD","paymentMethod":"pix"}"""), 422)
        assertThat(ProdutosStub.reservas).isEmpty()
    }

    @Test
    fun checkout_ComProdutorOuSemToken_RetornarErrorMessage403Ou401() {
        esperarErro(checkout(Tokens.produtorCarlos(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""), 403)
        esperarErro(testClient.post().uri("/api/v1/pedidos").exchange(), 401)
    }

    // ---------- POST /pedidos/{id}/pagamento ----------

    @Test
    fun pagamento_PedidoPendente_MarcarPagoComConfirmedAtUmaVezSo() {
        val pago = testClient.post().uri("/api/v1/pedidos/701/pagamento").headers(Tokens.clienteBia()).exchange()
            .expectStatus().isOk.expectBody(PedidoResponseDto::class.java).returnResult().responseBody!!
        assertThat(pago.payment.status).isEqualTo("paid")
        assertThat(pago.payment.method).isEqualTo("card")
        assertThat(pago.payment.confirmedAt).isNotNull()
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id = 951", String::class.java)).isEqualTo("paid")

        val erro = esperarErro(testClient.post().uri("/api/v1/pedidos/701/pagamento").headers(Tokens.clienteBia()).exchange(), 409)
        assertThat(erro.message).contains("paid")
    }

    @Test
    fun pagamento_PedidoDeOutroCliente_RetornarErrorMessageStatus404() {
        esperarErro(testClient.post().uri("/api/v1/pedidos/700/pagamento").headers(Tokens.clienteBia()).exchange(), 404)
        esperarErro(testClient.post().uri("/api/v1/pedidos/9999/pagamento").headers(Tokens.clienteBia()).exchange(), 404)
    }

    // ---------- GET /pedidos e /pedidos/{id} ----------

    @Test
    fun buscar_SoODono_OutroClienteRecebe404() {
        val doBob = pedido(700, Tokens.clienteBobSuspenso())
        assertThat(doBob.payment.status).isEqualTo("paid")
        assertThat(doBob.payment.method).isEqualTo("pix")
        assertThat(doBob.producers.single().deliveryStatus).isEqualTo("shipped")
        assertThat(doBob.totalAmount).isEqualByComparingTo("102.80")

        esperarErro(testClient.get().uri("/api/v1/pedidos/700").headers(Tokens.clienteBia()).exchange(), 404)
        esperarErro(testClient.get().uri("/api/v1/pedidos/abc").headers(Tokens.clienteBia()).exchange(), 400)
    }

    @Test
    fun historico_SoDoCliente_MaisRecentePrimeiro() {
        adicionar(Tokens.clienteBia(), 106, 1).expectStatus().isOk
        val novo = criado(checkout(Tokens.clienteBia(), """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""))

        val pagina = testClient.get().uri("/api/v1/pedidos").headers(Tokens.clienteBia()).exchange()
            .expectStatus().isOk.expectBody(PageableDto::class.java).returnResult().responseBody!!
        @Suppress("UNCHECKED_CAST")
        val ids = pagina.content.map { (it as Map<String, Any>)["id"] }
        assertThat(ids).containsExactly(novo.id, 701)
        assertThat(pagina.totalElements).isEqualTo(2)

        @Suppress("UNCHECKED_CAST")
        val antigo = pagina.content[1] as Map<String, Any>
        assertThat((antigo["producers"] as List<*>)).hasSize(1)

        val doBob = testClient.get().uri("/api/v1/pedidos").headers(Tokens.clienteBobSuspenso()).exchange()
            .expectStatus().isOk.expectBody(PageableDto::class.java).returnResult().responseBody!!
        assertThat(doBob.totalElements).isEqualTo(1)
    }
}
