package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutorAprovadoDto
import com.raizpet.api.web.dto.ProdutorMeDto
import com.raizpet.api.web.dto.ProdutorPublicoDto
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
 * Rotas públicas de produtores (GET /api/v1/produtores e /{id}) e a lista interna
 * GET /api/v1/interno/produtores/aprovados, usada pelo service-produtos.
 * Seed: 103 e 106 aprovados, 104 pendente, 105 rejeitado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProdutorIT {

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

    @Suppress("UNCHECKED_CAST")
    private fun PageableDto.ids(): List<Int> = content.map { (it as Map<String, Any>)["id"] as Int }

    // ---------- GET /produtores (público) ----------

    @Test
    fun listarProdutores_SemToken_RetornarSoAprovadosOrdenadosPorNomeComStatus200() {
        val responseBody = testClient.get().uri("/api/v1/produtores")
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        // "Apiário do Davi" (106) vem antes de "Sítio Raiz Orgânicos" (103); 104 e 105 nunca aparecem
        assertThat(responseBody!!.ids()).containsExactly(106, 103)
        assertThat(responseBody.totalElements).isEqualTo(2)
    }

    @Test
    fun listarProdutores_FormatoDoItem_ExpoeSoDadosPublicos() {
        val responseBody = testClient.get().uri("/api/v1/produtores?sort=id")
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody

        @Suppress("UNCHECKED_CAST")
        val item = responseBody!!.content[0] as Map<String, Any?>
        assertThat(item["id"]).isEqualTo(103)
        assertThat(item["companyName"]).isEqualTo("Sítio Raiz Orgânicos")
        assertThat(item["imageUrl"]).isEqualTo("/images/produtor-sitio-raiz.jpg")
        assertThat(item["city"]).isEqualTo("Atibaia")
        assertThat(item["state"]).isEqualTo("SP")
        assertThat(item["verified"]).isEqualTo(true)
        // Nada de dado pessoal ou interno na vitrine
        assertThat(item).doesNotContainKeys("email", "phone", "technicalResponsibleName", "status", "passwordHash")
    }

    @Test
    fun listarProdutores_ComPaginacao_RetornarPaginaSolicitada() {
        val responseBody = testClient.get().uri("/api/v1/produtores?size=1&page=1")
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody!!.ids()).containsExactly(103)
        assertThat(responseBody.totalPages).isEqualTo(2)
    }

    // ---------- GET /produtores/{id} (público) ----------

    @Test
    fun buscarProdutor_Aprovado_RetornarDadosPublicosComStatus200() {
        val responseBody = testClient.get().uri("/api/v1/produtores/106")
            .exchange()
            .expectStatus().isOk
            .expectBody(ProdutorPublicoDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.companyName).isEqualTo("Apiário do Davi")
        assertThat(responseBody.since.toString()).startsWith("2026-02-15")
        // Campos opcionais da decisão 5: produtor sem foto/cidade continua aparecendo
        assertThat(responseBody.imageUrl).isNull()
        assertThat(responseBody.city).isNull()
    }

    @Test
    fun buscarProdutor_PendenteRejeitadoClienteOuInexistente_RetornarErrorMessageStatus404() {
        // 104 pendente, 105 rejeitado, 101 é cliente, 999 não existe
        listOf(104, 105, 101, 999).forEach { id ->
            esperarErro(testClient.get().uri("/api/v1/produtores/$id").exchange(), 404)
        }
    }

    // ---------- GET /interno/produtores/aprovados ----------

    @Test
    fun listarAprovadosInterno_ComChave_RetornarTodosAprovadosPorIdComStatus200() {
        val responseBody = testClient.get().uri("/api/v1/interno/produtores/aprovados")
            .header("X-Internal-Key", chaveInterna)
            .exchange()
            .expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<ProdutorAprovadoDto>>() {})
            .returnResult().responseBody

        assertThat(responseBody).containsExactly(
            ProdutorAprovadoDto(103, "Sítio Raiz Orgânicos", "11.222.333/0001-81"),
            ProdutorAprovadoDto(106, "Apiário do Davi", "28.641.953/0001-58")
        )
    }

    @Test
    fun listarAprovadosInterno_SemChaveOuComChaveErrada_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/interno/produtores/aprovados").exchange(), 401)
        esperarErro(
            testClient.get().uri("/api/v1/interno/produtores/aprovados")
                .header("X-Internal-Key", "chave-errada").exchange(),
            401
        )
    }

    @Test
    fun listarAprovadosInterno_ComTokenDeUsuarioSemChave_RetornarErrorMessageStatus401() {
        // Token válido de ADMIN não abre rota interna: o que vale ali é a chave entre serviços
        esperarErro(
            testClient.get().uri("/api/v1/interno/produtores/aprovados")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
                .exchange(),
            401
        )
    }

    // ---------- GET /produtores/me (PRODUCER) ----------

    @Test
    fun buscarMeuPerfil_ComTokenDeProdutor_RetornarDadosDoTokenComStatus200() {
        val responseBody = testClient.get().uri("/api/v1/produtores/me")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "carlos@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(ProdutorMeDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isEqualTo(
            ProdutorMeDto(
                id = 103,
                companyName = "Sítio Raiz Orgânicos",
                shortDescription = "Rações naturais de frango e peixe orgânicos",
                imageUrl = "/images/produtor-sitio-raiz.jpg",
                city = "Atibaia",
                state = "SP",
                status = "approved"
            )
        )
    }

    @Test
    fun buscarMeuPerfil_ProdutorSemFotoECidade_RetornarCamposNulos() {
        val responseBody = testClient.get().uri("/api/v1/produtores/me")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "davi@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(ProdutorMeDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody!!.id).isEqualTo(106)
        assertThat(responseBody.imageUrl).isNull()
        assertThat(responseBody.city).isNull()
        assertThat(responseBody.state).isNull()
    }

    @Test
    fun buscarMeuPerfil_ComClienteOuAdmin_RetornarErrorMessageStatus403() {
        listOf("bia@email.com", "ana@email.com").forEach { email ->
            esperarErro(
                testClient.get().uri("/api/v1/produtores/me")
                    .headers(JwtAuthentication.getHeaderAuthorization(testClient, email))
                    .exchange(),
                403
            )
        }
    }

    @Test
    fun buscarMeuPerfil_SemToken_RetornarErrorMessageStatus401() {
        // Prova que a regra de /produtores/me vem antes do permitAll de /produtores/*
        esperarErro(testClient.get().uri("/api/v1/produtores/me").exchange(), 401)
        // E a rota pública vizinha continua aberta
        testClient.get().uri("/api/v1/produtores/103").exchange().expectStatus().isOk
    }
}
