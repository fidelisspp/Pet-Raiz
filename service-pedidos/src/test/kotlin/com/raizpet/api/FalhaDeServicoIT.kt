package com.raizpet.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Outro serviço respondendo errado (500, JSON inválido) através dos stubs: 503 no formato ErrorMessage,
 * sem mexer em carrinho, estoque ou pedidos. O caso "fora do ar de verdade" está em ServicosForaDoArIT.
 */
class FalhaDeServicoIT : IntegracaoBase() {

    private val corpo = """{"addressId":502,"shippingMethod":"standard","paymentMethod":"pix"}"""

    @Test
    fun carrinho_ComServicoDeProdutosComErro_RetornarErrorMessageStatus503() {
        listOf(ProdutosStub.Modo.ERRO_500, ProdutosStub.Modo.JSON_INVALIDO).forEach { modo ->
            ProdutosStub.modo = modo
            // Bob tem item no carrinho, então o GET consulta o service-produtos
            val erro = esperarErro(testClient.get().uri("/api/v1/carrinho").headers(Tokens.clienteBobSuspenso()).exchange(), 503)
            assertThat(erro.message).contains("produtos")
            esperarErro(adicionar(Tokens.clienteBia(), 100, 1), 503)
        }
        ProdutosStub.modo = ProdutosStub.Modo.OK
        assertThat(carrinho(Tokens.clienteBia()).items).isEmpty()
    }

    @Test
    fun checkout_ComServicoDeUsuariosComErro_RetornarErrorMessageStatus503SemReservar() {
        adicionar(Tokens.clienteBia(), 100, 1).expectStatus().isOk
        listOf(UsuariosStub.Modo.ERRO_500, UsuariosStub.Modo.JSON_INVALIDO).forEach { modo ->
            UsuariosStub.modo = modo
            val erro = esperarErro(post("/api/v1/pedidos", Tokens.clienteBia(), corpo.replace("502", "500")), 503)
            assertThat(erro.message).contains("usuários")
        }
        assertThat(ProdutosStub.reservas).isEmpty()
        assertThat(carrinho(Tokens.clienteBia()).items).hasSize(1)
    }

    @Test
    fun checkout_ComReservaRespondendo500_RetornarErrorMessageStatus503ECarrinhoIntacto() {
        adicionar(Tokens.clienteBia(), 100, 1).expectStatus().isOk
        ProdutosStub.modo = ProdutosStub.Modo.ERRO_500

        esperarErro(post("/api/v1/pedidos", Tokens.clienteBia(), corpo.replace("502", "500")), 503)
        assertThat(ProdutosStub.reservas).hasSize(1)
        assertThat(ProdutosStub.estornos).isEmpty()
        assertThat(contar("purchase_order")).isEqualTo(2)
        assertThat(contar("cart_item")).isEqualTo(2)
    }
}
