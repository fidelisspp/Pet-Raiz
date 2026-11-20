package com.raizpet.api

import com.raizpet.api.entity.Address
import com.raizpet.api.entity.PaymentMethod
import com.raizpet.api.entity.ShippingMethod
import com.raizpet.api.service.NovoPedido
import com.raizpet.api.service.PedidoTransacao
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

/**
 * Compensação simplificada (README): a reserva deu certo, mas a transação local do pedido falha.
 * A falha é forçada com um spy no PedidoTransacao (o resto do fluxo é o real, com os stubs HTTP).
 * Por causa do spy, esta classe sobe um contexto do Spring próprio.
 */
class CompensacaoIT : IntegracaoBase() {

    @MockitoSpyBean
    lateinit var pedidoTransacao: PedidoTransacao

    private val corpo = """{"addressId":500,"shippingMethod":"standard","paymentMethod":"pix"}"""

    @BeforeEach
    fun falharTransacaoLocal() {
        val qualquer = NovoPedido(0, Address(), ShippingMethod.standard, PaymentMethod.pix, emptyList(), emptyMap())
        Mockito.doThrow(IllegalStateException("falha simulada ao gravar o pedido"))
            .`when`(pedidoTransacao).criar(Mockito.any(NovoPedido::class.java) ?: qualquer)
    }

    private fun montarCarrinho() {
        adicionar(Tokens.clienteBia(), 100, 2).expectStatus().isOk
        adicionar(Tokens.clienteBia(), 106, 1).expectStatus().isOk
    }

    @Test
    fun checkout_TransacaoLocalFalha_EstornarAMesmaListaERetornarErrorMessageStatus500() {
        montarCarrinho()

        val erro = esperarErro(post("/api/v1/pedidos", Tokens.clienteBia(), corpo), 500)
        assertThat(erro.message).contains("estoque foi liberado")

        // Estorno com exatamente a mesma lista da reserva; estoque de volta ao original
        assertThat(ProdutosStub.reservas.single()).containsExactly(100 to 2, 106 to 1)
        assertThat(ProdutosStub.estornos.single()).isEqualTo(ProdutosStub.reservas.single())
        assertThat(ProdutosStub.estoque).isEqualTo(ProdutosStub.ESTOQUE_INICIAL)

        // Nada gravado aqui e o carrinho continua igual
        assertThat(contar("purchase_order")).isEqualTo(2)
        assertThat(carrinho(Tokens.clienteBia()).items.map { it.productId to it.quantity }).containsExactly(100 to 2, 106 to 1)
    }

    @Test
    fun checkout_TransacaoLocalEEstornoFalham_SoRegistrarLogERetornarErrorMessageStatus500() {
        montarCarrinho()
        ProdutosStub.modoEstorno = ProdutosStub.Modo.ERRO_500

        val erro = esperarErro(post("/api/v1/pedidos", Tokens.clienteBia(), corpo), 500)
        assertThat(erro.message).doesNotContain("estoque foi liberado")

        // Limitação aceita: estorno tentado uma vez só; o estoque fica baixado até a correção manual (pelo log)
        assertThat(ProdutosStub.estornos).hasSize(1)
        assertThat(ProdutosStub.estoque[100]).isEqualTo(8)
        assertThat(ProdutosStub.estoque[106]).isEqualTo(4)
        assertThat(contar("purchase_order")).isEqualTo(2)
        assertThat(carrinho(Tokens.clienteBia()).items).hasSize(2)
    }
}
