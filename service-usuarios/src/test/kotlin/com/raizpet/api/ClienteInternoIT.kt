package com.raizpet.api

import com.raizpet.api.web.dto.ClienteInternoDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * GET /api/v1/interno/clientes/{id}, usada pelo service-pedidos no checkout.
 * Seed: 101 (Bia) ativa, 102 (Bob) suspenso, 100 admin, 103 produtor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClienteInternoIT {

    @Autowired
    lateinit var testClient: RestTestClient

    @Value("\${petraiz.interno.chave}")
    lateinit var chaveInterna: String

    private fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int): ErrorMessage {
        val responseBody = spec
            .expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(status)
        return responseBody
    }

    private fun buscar(id: Int): ClienteInternoDto? =
        testClient.get().uri("/api/v1/interno/clientes/$id")
            .header("X-Internal-Key", chaveInterna)
            .exchange()
            .expectStatus().isOk
            .expectBody(ClienteInternoDto::class.java)
            .returnResult().responseBody

    @Test
    fun buscarClienteInterno_AtivoESuspenso_RetornarIdESuspendedComStatus200() {
        assertThat(buscar(101)).isEqualTo(ClienteInternoDto(101, false))
        assertThat(buscar(102)).isEqualTo(ClienteInternoDto(102, true))
    }

    @Test
    fun buscarClienteInterno_SoExpoeIdESuspended() {
        val json = testClient.get().uri("/api/v1/interno/clientes/101")
            .header("X-Internal-Key", chaveInterna)
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult().responseBody
        assertThat(json!!.keys).containsExactlyInAnyOrder("id", "suspended")
    }

    @Test
    fun buscarClienteInterno_AdminProdutorOuInexistente_RetornarErrorMessageStatus404() {
        listOf(100, 103, 999).forEach { id ->
            esperarErro(
                testClient.get().uri("/api/v1/interno/clientes/$id").header("X-Internal-Key", chaveInterna).exchange(),
                404
            )
        }
    }

    @Test
    fun buscarClienteInterno_SemChaveChaveErradaOuSoComToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/interno/clientes/101").exchange(), 401)
        esperarErro(
            testClient.get().uri("/api/v1/interno/clientes/101").header("X-Internal-Key", "chave-errada").exchange(),
            401
        )
        // Nem o token da própria cliente abre a rota interna
        esperarErro(
            testClient.get().uri("/api/v1/interno/clientes/101")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "bia@email.com"))
                .exchange(),
            401
        )
    }
}
