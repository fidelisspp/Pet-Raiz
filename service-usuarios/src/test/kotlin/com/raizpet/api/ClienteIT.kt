package com.raizpet.api

import com.raizpet.api.web.dto.ClienteResponseDto
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClienteIT {

    @Autowired
    lateinit var testClient: RestTestClient

    private fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int) {
        val responseBody = spec
            .expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(status)
    }

    // ---------- GET /clientes/{id} ----------

    @Test
    fun buscarCliente_ComIdExistentePeloAdmin_RetornarClienteComStatus200() {
        val responseBody = testClient
            .get()
            .uri("/api/v1/clientes/101")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(ClienteResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.id).isEqualTo(101)
        assertThat(responseBody.cpf).isEqualTo("790.744.260-50")
        assertThat(responseBody.suspended).isFalse()
    }

    @Test
    fun buscarCliente_ComIdInexistentePeloAdmin_RetornarErrorMessageStatus404() {
        esperarErro(
            testClient.get().uri("/api/v1/clientes/0")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
                .exchange(),
            404
        )
        // Id de um produtor não é cliente
        esperarErro(
            testClient.get().uri("/api/v1/clientes/103")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
                .exchange(),
            404
        )
    }

    @Test
    fun buscarCliente_ComClienteOuProdutor_RetornarErrorMessageStatus403() {
        listOf("bia@email.com", "carlos@email.com").forEach { email ->
            esperarErro(
                testClient.get().uri("/api/v1/clientes/101")
                    .headers(JwtAuthentication.getHeaderAuthorization(testClient, email))
                    .exchange(),
                403
            )
        }
    }

    @Test
    fun buscarCliente_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/clientes/101").exchange(), 401)
    }

    // ---------- GET /clientes ----------

    @Test
    fun buscarClientes_ComPaginacaoPeloAdmin_RetornarClientesComStatus200() {
        var responseBody = testClient
            .get()
            .uri("/api/v1/clientes")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.content).hasSize(2)
        assertThat(responseBody.number).isEqualTo(0)
        assertThat(responseBody.totalPages).isEqualTo(1)
        assertThat(responseBody.totalElements).isEqualTo(2)

        responseBody = testClient
            .get()
            .uri("/api/v1/clientes?size=1&page=1")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.content).hasSize(1)
        assertThat(responseBody.number).isEqualTo(1)
        assertThat(responseBody.totalPages).isEqualTo(2)
        // Ordenado por nome: "Bia" (página 0), "Bob" (página 1), como no seed-insert.sql
        @Suppress("UNCHECKED_CAST")
        val cliente = responseBody.content[0] as Map<String, Any>
        assertThat(cliente["name"]).isEqualTo("Bob")
        assertThat(cliente["suspended"]).isEqualTo(true)
    }

    @Test
    fun buscarClientes_ComClienteOuProdutor_RetornarErrorMessageStatus403() {
        listOf("bia@email.com", "carlos@email.com").forEach { email ->
            esperarErro(
                testClient.get().uri("/api/v1/clientes")
                    .headers(JwtAuthentication.getHeaderAuthorization(testClient, email))
                    .exchange(),
                403
            )
        }
    }

    @Test
    fun buscarClientes_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/clientes").exchange(), 401)
    }
}
