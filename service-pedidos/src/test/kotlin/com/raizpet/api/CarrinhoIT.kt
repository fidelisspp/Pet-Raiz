package com.raizpet.api

import com.raizpet.api.web.dto.CarrinhoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * Carrinho (CUSTOMER). Produtos e estoque vêm do ProdutosStub:
 * 100 = 89.90 (10 un., produtor 103), 101 = 19.50 (30), 102 = sem estoque, 103 = inativo, 106 = 30.00 (5, produtor 106).
 * Seed: o Bob (102) tem o carrinho 600 com 1x produto 100; a Bia (101) não tem carrinho.
 */
class CarrinhoIT : IntegracaoBase() {

    private fun patch(productId: Int, quantity: Int) =
        testClient.patch().uri("/api/v1/carrinho/itens/$productId").headers(Tokens.clienteBia())
            .contentType(MediaType.APPLICATION_JSON).body("""{"quantity":$quantity}""").exchange()

    private fun ok(spec: org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec): CarrinhoResponseDto =
        spec.expectStatus().isOk.expectBody(CarrinhoResponseDto::class.java).returnResult().responseBody!!

    // ---------- acesso ----------

    @Test
    fun carrinho_SemTokenOuComProdutor_RetornarErrorMessage401Ou403() {
        esperarErro(testClient.get().uri("/api/v1/carrinho").exchange(), 401)
        esperarErro(testClient.get().uri("/api/v1/carrinho").headers(Tokens.produtorCarlos()).exchange(), 403)
        esperarErro(adicionar(Tokens.adminAna(), 100, 1), 403)
    }

    // ---------- GET ----------

    @Test
    fun ver_PrimeiroAcesso_CriarCarrinhoVazioSemChamarOServicoDeProdutos() {
        assertThat(contar("cart")).isEqualTo(1)
        val primeiro = carrinho(Tokens.clienteBia())
        assertThat(primeiro.items).isEmpty()
        assertThat(primeiro.totalQuantity).isZero()
        assertThat(primeiro.subtotal).isEqualByComparingTo("0")
        assertThat(contar("cart")).isEqualTo(2)

        // Segundo acesso: o mesmo carrinho
        assertThat(carrinho(Tokens.clienteBia()).id).isEqualTo(primeiro.id)
        assertThat(contar("cart")).isEqualTo(2)
        assertThat(ProdutosStub.consultas.get()).isZero()
    }

    @Test
    fun ver_ComItens_TrazerDadosDoServicoDeProdutosESubtotal() {
        val c = carrinho(Tokens.clienteBobSuspenso())
        assertThat(c.id).isEqualTo(600)
        val item = c.items.single()
        assertThat(item.productId).isEqualTo(100)
        assertThat(item.name).isEqualTo("Ração Orgânica de Frango")
        assertThat(item.price).isEqualByComparingTo("89.90")
        assertThat(item.stock).isEqualTo(10)
        assertThat(item.imageUrl).isEqualTo("/images/frango.jpg")
        assertThat(item.producerId).isEqualTo(103)
        assertThat(item.available).isTrue()
        assertThat(c.subtotal).isEqualByComparingTo("89.90")
        // Uma chamada ao service-produtos por GET
        assertThat(ProdutosStub.consultas.get()).isEqualTo(1)
    }

    @Test
    fun ver_ComProdutoDesativadoOuRemovido_MarcarIndisponivelEForaDoSubtotal() {
        adicionar(Tokens.clienteBia(), 100, 2).expectStatus().isOk
        adicionar(Tokens.clienteBia(), 106, 1).expectStatus().isOk
        ProdutosStub.produtos.getValue(100).active = false

        var c = carrinho(Tokens.clienteBia())
        assertThat(c.items.first { it.productId == 100 }.available).isFalse()
        assertThat(c.items.first { it.productId == 100 }.subtotal).isEqualByComparingTo("0")
        assertThat(c.subtotal).isEqualByComparingTo("30.00")
        assertThat(c.totalQuantity).isEqualTo(3)

        ProdutosStub.produtos.remove(106)
        c = carrinho(Tokens.clienteBia())
        val sumido = c.items.first { it.productId == 106 }
        assertThat(sumido.available).isFalse()
        assertThat(sumido.name).isNull()
        assertThat(c.subtotal).isEqualByComparingTo("0")
    }

    // ---------- POST /itens ----------

    @Test
    fun adicionar_ProdutoNovoEDepoisOMesmo_SomarQuantidades() {
        var c = ok(adicionar(Tokens.clienteBia(), 100, 2))
        assertThat(c.items.single().quantity).isEqualTo(2)
        assertThat(c.subtotal).isEqualByComparingTo("179.80")

        c = ok(adicionar(Tokens.clienteBia(), 100, 3))
        assertThat(c.items.single().quantity).isEqualTo(5)

        c = ok(adicionar(Tokens.clienteBia(), 106, 1))
        assertThat(c.items.map { it.productId }).containsExactly(100, 106)
        assertThat(c.totalQuantity).isEqualTo(6)
        assertThat(c.subtotal).isEqualByComparingTo("479.50")
        assertThat(contar("cart_item")).isEqualTo(3) // 2 da Bia + 1 do Bob
        // Adicionar não reserva nada
        assertThat(ProdutosStub.estoque).isEqualTo(ProdutosStub.ESTOQUE_INICIAL)
        assertThat(ProdutosStub.reservas).isEmpty()
    }

    @Test
    fun adicionar_PassandoDoEstoque_RetornarErrorMessageStatus409EManterOCarrinho() {
        val erro = esperarErro(adicionar(Tokens.clienteBia(), 100, 11), 409)
        assertThat(erro.errors!!["100"]).contains("disponível 10")
        assertThat(carrinho(Tokens.clienteBia()).items).isEmpty()

        adicionar(Tokens.clienteBia(), 100, 5).expectStatus().isOk
        // 5 já no carrinho + 6 = 11 > 10
        val erroSoma = esperarErro(adicionar(Tokens.clienteBia(), 100, 6), 409)
        assertThat(erroSoma.errors!!["100"]).contains("5 já no carrinho")
        assertThat(carrinho(Tokens.clienteBia()).items.single().quantity).isEqualTo(5)

        // Sem estoque nenhum
        esperarErro(adicionar(Tokens.clienteBia(), 102, 1), 409)
    }

    @Test
    fun adicionar_ProdutoInativoOuInexistente_RetornarErrorMessage409Ou404() {
        val inativo = esperarErro(adicionar(Tokens.clienteBia(), 103, 1), 409)
        assertThat(inativo.errors!!["103"]).isEqualTo("produto inativo")
        esperarErro(adicionar(Tokens.clienteBia(), 999, 1), 404)
        assertThat(carrinho(Tokens.clienteBia()).items).isEmpty()
    }

    @Test
    fun adicionar_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        listOf("""{"quantity":1}""", """{"productId":100}""", """{"productId":100,"quantity":0}""",
            """{"productId":100,"quantity":-3}""", """{"productId":100,"quantity":10001}""").forEach {
            esperarErro(post("/api/v1/carrinho/itens", Tokens.clienteBia(), it), 422)
        }
        esperarErro(post("/api/v1/carrinho/itens", Tokens.clienteBia(), """{"productId":"abc","quantity":1}"""), 400)
    }

    // ---------- PATCH /itens/{productId} ----------

    @Test
    fun alterar_Quantidade_SubstituirOValor() {
        adicionar(Tokens.clienteBia(), 100, 2).expectStatus().isOk
        val c = ok(patch(100, 10))
        assertThat(c.items.single().quantity).isEqualTo(10)
        assertThat(c.subtotal).isEqualByComparingTo("899.00")
        assertThat(ok(patch(100, 1)).items.single().quantity).isEqualTo(1)
    }

    @Test
    fun alterar_AcimaDoEstoqueForaDoCarrinhoOuInvalido_RetornarErrorMessage() {
        adicionar(Tokens.clienteBia(), 100, 2).expectStatus().isOk
        esperarErro(patch(100, 11), 409)
        esperarErro(patch(106, 1), 404)       // não está no carrinho da Bia
        esperarErro(patch(100, 0), 422)
        assertThat(carrinho(Tokens.clienteBia()).items.single().quantity).isEqualTo(2)
    }

    // ---------- DELETE /itens/{productId} ----------

    @Test
    fun remover_Item_RetornarStatus204ESemChamarOServicoDeProdutos() {
        adicionar(Tokens.clienteBia(), 100, 2).expectStatus().isOk
        adicionar(Tokens.clienteBia(), 106, 1).expectStatus().isOk
        ProdutosStub.consultas.set(0)

        testClient.delete().uri("/api/v1/carrinho/itens/100").headers(Tokens.clienteBia()).exchange().expectStatus().isNoContent
        assertThat(ProdutosStub.consultas.get()).isZero()
        assertThat(carrinho(Tokens.clienteBia()).items.map { it.productId }).containsExactly(106)

        esperarErro(testClient.delete().uri("/api/v1/carrinho/itens/100").headers(Tokens.clienteBia()).exchange(), 404)
    }

    @Test
    fun carrinhos_SaoIsoladosPorCliente() {
        // Bia não enxerga nem mexe no item do carrinho do Bob
        esperarErro(testClient.delete().uri("/api/v1/carrinho/itens/100").headers(Tokens.clienteBia()).exchange(), 404)
        esperarErro(patch(100, 1), 404)
        assertThat(carrinho(Tokens.clienteBobSuspenso()).items.single().quantity).isEqualTo(1)
    }
}
