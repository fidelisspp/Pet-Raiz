package com.raizpet.api

import com.raizpet.api.web.dto.ClienteComprasDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import java.math.BigDecimal

/**
 * Etapa 5.3: resumo de compras por cliente (ADMIN), para a tela Clientes do admin.
 * Seed: pedido 700 do Bob (102) PAGO, total 102.80; pedido 701 da Bia (101) com pagamento pendente.
 */
class PedidoAdminIT : IntegracaoBase() {

    private fun compras(ids: String): List<ClienteComprasDto> =
        testClient.get().uri("/api/v1/pedidos/admin/clientes?ids=$ids").headers(Tokens.adminAna())
            .exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<ClienteComprasDto>>() {})
            .returnResult().responseBody!!

    @Test
    fun compras_ContarSoPedidosPagosNaOrdemPedida() {
        assertThat(compras("101,102,999")).containsExactly(
            // Bia só tem pedido com pagamento pendente: não conta
            ClienteComprasDto(101, 0, BigDecimal("0.00")),
            ClienteComprasDto(102, 1, BigDecimal("102.80")),
            ClienteComprasDto(999, 0, BigDecimal("0.00"))
        )
    }

    @Test
    fun compras_IdsRepetidos_ApareceremUmaVez() {
        assertThat(compras("102,102").map { it.customerId }).containsExactly(102)
    }

    @Test
    fun compras_IdsVaziosInvalidosOuDemais_Retornar400() {
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin/clientes").headers(Tokens.adminAna()).exchange(), 400)
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin/clientes?ids=abc").headers(Tokens.adminAna()).exchange(), 400)
        val muitos = (1..101).joinToString(",")
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin/clientes?ids=$muitos").headers(Tokens.adminAna()).exchange(), 400)
    }

    @Test
    fun compras_ComClienteProdutorOuSemToken_Retornar403e401() {
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin/clientes?ids=101").headers(Tokens.clienteBia()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin/clientes?ids=101").headers(Tokens.produtorCarlos()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin/clientes?ids=101").exchange(), 401)
    }

    @Test
    fun rotasDoCliente_ContinuamSoParaCliente() {
        // O controller do admin não abriu as rotas do cliente para o admin
        esperarErro(testClient.get().uri("/api/v1/pedidos").headers(Tokens.adminAna()).exchange(), 403)
    }
}
