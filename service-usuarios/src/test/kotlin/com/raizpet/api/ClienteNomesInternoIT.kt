package com.raizpet.api

import com.raizpet.api.web.dto.ClienteNomeInternoDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Etapa 5.5: GET /api/v1/interno/clientes?ids= ou ?busca=, usada pela lista de pedidos do admin (service-pedidos).
 * Seed: 101 Bia e 102 Bob são clientes; 100 é admin e 103 produtor (não aparecem).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClienteNomesInternoIT {

    @Autowired
    lateinit var testClient: RestTestClient

    @Value("\${petraiz.interno.chave}")
    lateinit var chaveInterna: String

    private fun nomes(query: String): List<ClienteNomeInternoDto> =
        testClient.get().uri("/api/v1/interno/clientes$query").header("X-Internal-Key", chaveInterna)
            .exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<ClienteNomeInternoDto>>() {})
            .returnResult().responseBody!!

    @Test
    fun porIds_SoClientes() {
        assertThat(nomes("?ids=101,102,100,103,999").map { it.id to it.name })
            .containsExactlyInAnyOrder(101 to "Bia", 102 to "Bob")
    }

    @Test
    fun porBusca_NomeSemDiferenciarMaiuscula() {
        assertThat(nomes("?busca=BO").map { it.id }).containsExactly(102)
        assertThat(nomes("?busca=ninguem")).isEmpty()
    }

    @Test
    fun semParametroOuIdsDemais_Retornar400() {
        testClient.get().uri("/api/v1/interno/clientes").header("X-Internal-Key", chaveInterna)
            .exchange().expectStatus().isBadRequest.expectBody(ErrorMessage::class.java)
        val muitos = (1..101).joinToString(",")
        testClient.get().uri("/api/v1/interno/clientes?ids=$muitos").header("X-Internal-Key", chaveInterna)
            .exchange().expectStatus().isBadRequest
    }

    @Test
    fun semChaveOuComTokenDeAdmin_Retornar401() {
        testClient.get().uri("/api/v1/interno/clientes?ids=101").exchange().expectStatus().isUnauthorized
        testClient.get().uri("/api/v1/interno/clientes?ids=101")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
            .exchange().expectStatus().isUnauthorized
    }
}
