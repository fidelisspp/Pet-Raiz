package com.raizpet.api

import com.raizpet.api.web.dto.ClienteResponseDto
import com.raizpet.api.web.dto.ClienteSuspensaoDto
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.UsuarioLoginDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Etapa 5.3: busca, filtro e suspensão de clientes pelo ADMIN.
 * Seed: 101 Bia (bia@email.com, ativa) e 102 Bob (bob@email.com, suspenso).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClienteAdminIT {

    @Autowired
    lateinit var testClient: RestTestClient

    @Value("\${petraiz.interno.chave}")
    lateinit var chaveInterna: String

    private fun admin() = JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com")
    private fun cliente() = JwtAuthentication.getHeaderAuthorization(testClient, "bia@email.com")

    private fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int): ErrorMessage {
        val erro = spec.expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java).returnResult().responseBody
        assertThat(erro).isNotNull
        assertThat(erro!!.status).isEqualTo(status)
        return erro
    }

    @Suppress("UNCHECKED_CAST")
    private fun ids(uri: String): List<Int> = testClient.get().uri(uri).headers(admin())
        .exchange().expectStatus().isOk
        .expectBody(PageableDto::class.java).returnResult().responseBody!!
        .content.map { (it as Map<String, Any>)["id"] as Int }

    private fun suspensao(id: Int, suspenso: Boolean?) = testClient.patch().uri("/api/v1/clientes/$id/suspensao")
        .headers(admin()).contentType(MediaType.APPLICATION_JSON).body(ClienteSuspensaoDto(suspenso)).exchange()

    // ---------- GET /clientes com filtros ----------

    @Test
    fun listar_SemFiltro_ManterContratoAntigo() {
        assertThat(ids("/api/v1/clientes")).containsExactly(101, 102)
    }

    @Test
    fun listar_BuscaPorNomeOuEmailSemDiferenciarMaiuscula() {
        assertThat(ids("/api/v1/clientes?busca=BI")).containsExactly(101)
        assertThat(ids("/api/v1/clientes?busca=bob@email")).containsExactly(102)
        assertThat(ids("/api/v1/clientes?busca=ninguem")).isEmpty()
        // Busca em branco = sem busca (a URL é montada pelo builder, que codifica os espaços uma vez só)
        @Suppress("UNCHECKED_CAST")
        val emBranco = testClient.get().uri { it.path("/api/v1/clientes").queryParam("busca", "  ").build() }
            .headers(admin()).exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
            .content.map { (it as Map<String, Any>)["id"] as Int }
        assertThat(emBranco).containsExactly(101, 102)
    }

    @Test
    fun listar_FiltroSuspenso() {
        assertThat(ids("/api/v1/clientes?suspenso=true")).containsExactly(102)
        assertThat(ids("/api/v1/clientes?suspenso=false")).containsExactly(101)
        assertThat(ids("/api/v1/clientes?suspenso=false&busca=bob")).isEmpty()
    }

    @Test
    fun listar_FiltroInvalido_Retornar400() {
        esperarErro(testClient.get().uri("/api/v1/clientes?suspenso=talvez").headers(admin()).exchange(), 400)
    }

    // ---------- PATCH /clientes/{id}/suspensao ----------

    @Test
    fun suspender_Retornar200EBloquearLoginEChecagemInterna() {
        val dto = suspensao(101, true).expectStatus().isOk
            .expectBody(ClienteResponseDto::class.java).returnResult().responseBody!!
        assertThat(dto.suspended).isTrue

        val erro = esperarErro(
            testClient.post().uri("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .body(UsuarioLoginDto("bia@email.com", JwtAuthentication.SENHA)).exchange(),
            403
        )
        assertThat(erro.message).isEqualTo("Conta suspensa")

        // O checkout do service-pedidos consulta esta rota
        testClient.get().uri("/api/v1/interno/clientes/101").header("X-Internal-Key", chaveInterna)
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.suspended").isEqualTo(true)
    }

    @Test
    fun reativar_Retornar200ELiberarLogin() {
        val dto = suspensao(102, false).expectStatus().isOk
            .expectBody(ClienteResponseDto::class.java).returnResult().responseBody!!
        assertThat(dto.suspended).isFalse
        testClient.post().uri("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
            .body(UsuarioLoginDto("bob@email.com", JwtAuthentication.SENHA)).exchange().expectStatus().isOk
    }

    @Test
    fun suspender_MesmoValor_SerIdempotente() {
        suspensao(102, true).expectStatus().isOk
    }

    @Test
    fun suspender_CorpoSemCampoOuInexistenteOuNaoCliente_Retornar422e404() {
        assertThat(esperarErro(suspensao(101, null), 422).errors).containsKey("suspended")
        esperarErro(suspensao(9999, true), 404)
        // 103 é produtor, não cliente
        esperarErro(suspensao(103, true), 404)
    }

    @Test
    fun suspender_ComClienteOuSemToken_Retornar403e401() {
        esperarErro(
            testClient.patch().uri("/api/v1/clientes/102/suspensao").headers(cliente())
                .contentType(MediaType.APPLICATION_JSON).body(ClienteSuspensaoDto(false)).exchange(),
            403
        )
        esperarErro(
            testClient.patch().uri("/api/v1/clientes/102/suspensao")
                .contentType(MediaType.APPLICATION_JSON).body(ClienteSuspensaoDto(false)).exchange(),
            401
        )
    }
}
