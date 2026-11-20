package com.raizpet.api

import com.raizpet.api.web.dto.EnderecoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.function.Consumer

/**
 * Endereços (CUSTOMER). Seed: 500 e 501 da Bia (101; o 501 é usado no pedido 701), 502 do Bob (102).
 * Nenhuma rota de endereço chama outro serviço.
 */
class EnderecoIT : IntegracaoBase() {

    private val tipoLista = object : ParameterizedTypeReference<List<EnderecoResponseDto>>() {}

    private val novo = """{"label":" Sítio ","recipientName":"Bia Souza","street":"Estrada Velha","number":"s/n",
        "complement":"","neighborhood":"Zona Rural","city":"Bragança Paulista","state":"sp","zipCode":"12900123"}"""

    private fun listar(quem: Consumer<HttpHeaders>): List<EnderecoResponseDto> =
        testClient.get().uri("/api/v1/enderecos").headers(quem).exchange()
            .expectStatus().isOk
            .expectBody(tipoLista).returnResult().responseBody!!

    private fun put(id: Int, quem: Consumer<HttpHeaders>, corpo: String) =
        testClient.put().uri("/api/v1/enderecos/$id").headers(quem).contentType(MediaType.APPLICATION_JSON).body(corpo).exchange()

    // ---------- acesso ----------

    @Test
    fun enderecos_SemTokenOuComTokenInvalido_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/enderecos").exchange(), 401)
        esperarErro(testClient.get().uri("/api/v1/enderecos").headers(Tokens.header("lixo")).exchange(), 401)
        esperarErro(testClient.get().uri("/api/v1/enderecos").headers(Tokens.header(Tokens.criar(101, "CUSTOMER", expiraEmMs = -1000))).exchange(), 401)
        esperarErro(testClient.get().uri("/api/v1/enderecos").headers(Tokens.header(Tokens.criar(101, "CUSTOMER", secret = "outra-chave-outra-chave-outra-chave"))).exchange(), 401)
        // Token sem a claim id
        esperarErro(testClient.get().uri("/api/v1/enderecos").headers(Tokens.header(Tokens.criar(null, "CUSTOMER"))).exchange(), 401)
    }

    @Test
    fun enderecos_ComProdutorOuAdmin_RetornarErrorMessageStatus403() {
        esperarErro(testClient.get().uri("/api/v1/enderecos").headers(Tokens.produtorCarlos()).exchange(), 403)
        esperarErro(post("/api/v1/enderecos", Tokens.adminAna(), novo), 403)
    }

    // ---------- GET ----------

    @Test
    fun listar_RetornarSoOsEnderecosDoIdDoToken() {
        val daBia = listar(Tokens.clienteBia())
        assertThat(daBia.map { it.id }).containsExactly(500, 501)
        assertThat(daBia[0].recipientName).isEqualTo("Bia Souza")
        assertThat(daBia[0].complement).isEqualTo("Apto 12")
        assertThat(listar(Tokens.clienteBobSuspenso()).map { it.id }).containsExactly(502)
        assertThat(listar(Tokens.header(Tokens.criar(555, "CUSTOMER")))).isEmpty()
    }

    @Test
    fun buscar_DeOutroClienteOuInexistente_RetornarErrorMessageStatus404() {
        testClient.get().uri("/api/v1/enderecos/500").headers(Tokens.clienteBia()).exchange().expectStatus().isOk
        esperarErro(testClient.get().uri("/api/v1/enderecos/502").headers(Tokens.clienteBia()).exchange(), 404)
        esperarErro(testClient.get().uri("/api/v1/enderecos/9999").headers(Tokens.clienteBia()).exchange(), 404)
    }

    // ---------- POST ----------

    @Test
    fun criar_ComDadosValidos_RetornarStatus201ENormalizarCampos() {
        val criado = post("/api/v1/enderecos", Tokens.clienteBia(), novo)
            .expectStatus().isCreated
            .expectBody(EnderecoResponseDto::class.java).returnResult().responseBody!!

        assertThat(criado.id).isNotNull()
        assertThat(criado.label).isEqualTo("Sítio")
        assertThat(criado.recipientName).isEqualTo("Bia Souza")
        assertThat(criado.complement).isNull()
        assertThat(criado.state).isEqualTo("SP")
        assertThat(criado.zipCode).isEqualTo("12900-123")
        // Dono = id do token
        assertThat(listar(Tokens.clienteBia()).map { it.id }).containsExactly(500, 501, criado.id)
        assertThat(jdbc.queryForObject("SELECT customer_id FROM address WHERE id = ?", Int::class.java, criado.id)).isEqualTo(101)
    }

    @Test
    fun criar_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        val erro = esperarErro(
            post(
                "/api/v1/enderecos", Tokens.clienteBia(),
                """{"recipientName":" ","street":"Rua","number":"12345678901","neighborhood":"B","city":"C","state":"SPP","zipCode":"123"}"""
            ),
            422
        )
        assertThat(erro.errors).containsOnlyKeys("recipientName", "number", "state", "zipCode")
        esperarErro(post("/api/v1/enderecos", Tokens.clienteBia(), "{}"), 422)
        esperarErro(post("/api/v1/enderecos", Tokens.clienteBia(), "{json quebrado"), 400)
        assertThat(contar("address")).isEqualTo(3)
    }

    // ---------- PUT ----------

    @Test
    fun atualizar_EnderecoProprio_RetornarStatus200ComDadosNovos() {
        val alterado = put(500, Tokens.clienteBia(), novo)
            .expectStatus().isOk
            .expectBody(EnderecoResponseDto::class.java).returnResult().responseBody!!
        assertThat(alterado.id).isEqualTo(500)
        assertThat(alterado.street).isEqualTo("Estrada Velha")
        assertThat(alterado.city).isEqualTo("Bragança Paulista")
    }

    @Test
    fun atualizar_DeOutroClienteOuInvalido_RetornarErrorMessage404Ou422() {
        esperarErro(put(502, Tokens.clienteBia(), novo), 404)
        assertThat(jdbc.queryForObject("SELECT street FROM address WHERE id = 502", String::class.java)).isEqualTo("Rua B")
        esperarErro(put(500, Tokens.clienteBia(), """{"recipientName":"X"}"""), 422)
    }

    @Test
    fun atualizar_EnderecoUsadoEmPedido_RetornarErrorMessageStatus409SemAlterar() {
        // 501 é o endereço do pedido 701 da Bia
        val erro = esperarErro(put(501, Tokens.clienteBia(), novo), 409)
        assertThat(erro.message).contains("pedido").contains("alterado")
        assertThat(jdbc.queryForObject("SELECT street FROM address WHERE id = 501", String::class.java)).isEqualTo("Av. Paulista")

        // O pedido continua mostrando o endereço original
        testClient.get().uri("/api/v1/pedidos/701").headers(Tokens.clienteBia()).exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.address.street").isEqualTo("Av. Paulista")
    }

    @Test
    fun atualizar_EnderecoUsadoEmPedidoDeOutroCliente_Retornar404AntesDo409() {
        // 502 é do Bob e foi usado no pedido 700: para a Bia ele "não existe"
        esperarErro(put(502, Tokens.clienteBia(), novo), 404)
    }

    // ---------- DELETE ----------

    @Test
    fun excluir_EnderecoSemPedido_RetornarStatus204() {
        testClient.delete().uri("/api/v1/enderecos/500").headers(Tokens.clienteBia()).exchange().expectStatus().isNoContent
        assertThat(listar(Tokens.clienteBia()).map { it.id }).containsExactly(501)
        esperarErro(testClient.delete().uri("/api/v1/enderecos/500").headers(Tokens.clienteBia()).exchange(), 404)
    }

    @Test
    fun excluir_EnderecoUsadoEmPedido_RetornarErrorMessageStatus409() {
        val erro = esperarErro(testClient.delete().uri("/api/v1/enderecos/501").headers(Tokens.clienteBia()).exchange(), 409)
        assertThat(erro.message).contains("pedido")
        assertThat(contar("address")).isEqualTo(3)
    }

    @Test
    fun excluir_DeOutroCliente_RetornarErrorMessageStatus404() {
        esperarErro(testClient.delete().uri("/api/v1/enderecos/502").headers(Tokens.clienteBia()).exchange(), 404)
        assertThat(contar("address")).isEqualTo(3)
    }
}
