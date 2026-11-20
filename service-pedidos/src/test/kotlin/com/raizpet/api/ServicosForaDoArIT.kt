package com.raizpet.api

import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * service-produtos e service-usuarios realmente fora do ar (conexão recusada): as URLs apontam para
 * uma porta sem ninguém. Não estende IntegracaoBase porque lá as URLs são trocadas pelos stubs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@TestPropertySource(properties = ["petraiz.produtos.url=http://localhost:1", "petraiz.usuarios.url=http://localhost:1"])
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ServicosForaDoArIT {

    @Autowired
    lateinit var testClient: RestTestClient

    private fun esperar503(spec: RestTestClient.ResponseSpec, path: String) {
        val erro = spec.expectStatus().isEqualTo(503)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(erro).isNotNull
        assertThat(erro!!.status).isEqualTo(503)
        assertThat(erro.path).isEqualTo(path)
    }

    @Test
    fun carrinhoECheckout_ComServicosDesligados_RetornarErrorMessageStatus503() {
        esperar503(testClient.get().uri("/api/v1/carrinho").headers(Tokens.clienteBobSuspenso()).exchange(), "/api/v1/carrinho")
        esperar503(
            testClient.post().uri("/api/v1/carrinho/itens").headers(Tokens.clienteBia())
                .contentType(MediaType.APPLICATION_JSON).body("""{"productId":100,"quantity":1}""").exchange(),
            "/api/v1/carrinho/itens"
        )
        // Checkout para no service-usuarios (antes de qualquer reserva)
        esperar503(
            testClient.post().uri("/api/v1/pedidos").headers(Tokens.clienteBobSuspenso())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"addressId":502,"shippingMethod":"standard","paymentMethod":"pix"}""").exchange(),
            "/api/v1/pedidos"
        )
    }

    @Test
    fun enderecosPedidosECarrinhoVazio_ComServicosDesligados_ContinuamFuncionando() {
        testClient.get().uri("/api/v1/enderecos").headers(Tokens.clienteBia()).exchange().expectStatus().isOk
        testClient.get().uri("/api/v1/pedidos").headers(Tokens.clienteBia()).exchange().expectStatus().isOk
        testClient.get().uri("/api/v1/pedidos/701").headers(Tokens.clienteBia()).exchange().expectStatus().isOk
        testClient.post().uri("/api/v1/pedidos/701/pagamento").headers(Tokens.clienteBia()).exchange().expectStatus().isOk
        // Carrinho vazio não precisa do service-produtos
        testClient.get().uri("/api/v1/carrinho").headers(Tokens.clienteBia()).exchange().expectStatus().isOk
        testClient.delete().uri("/api/v1/carrinho/itens/100").headers(Tokens.clienteBobSuspenso()).exchange().expectStatus().isNoContent
    }
}
