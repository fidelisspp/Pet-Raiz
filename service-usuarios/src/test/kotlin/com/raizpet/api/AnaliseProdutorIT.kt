package com.raizpet.api

import com.raizpet.api.jwt.JwtToken
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutorCadastroDto
import com.raizpet.api.web.dto.ProdutorCreateDto
import com.raizpet.api.web.dto.ProdutorResponseDto
import com.raizpet.api.web.dto.ProdutorStatusDto
import com.raizpet.api.web.dto.UsuarioLoginDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.util.LinkedMultiValueMap

/**
 * Etapa 5.1: análise de cadastros de produtor pelo ADMIN e documento enviado no cadastro.
 * Seed: 100 admin (ana), 101 cliente (bia), 103/106 aprovados, 104 pendente (paula), 105 rejeitado (rita).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AnaliseProdutorIT {

    @Autowired
    lateinit var testClient: RestTestClient

    private fun admin() = JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com")
    private fun cliente() = JwtAuthentication.getHeaderAuthorization(testClient, "bia@email.com")

    private fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int): ErrorMessage {
        val erro = spec.expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(erro).isNotNull
        assertThat(erro!!.status).isEqualTo(status)
        return erro
    }

    @Suppress("UNCHECKED_CAST")
    private fun PageableDto.ids(): List<Int> = content.map { (it as Map<String, Any>)["id"] as Int }

    private fun patchStatus(id: Int, corpo: ProdutorStatusDto) = testClient.patch()
        .uri("/api/v1/produtores/$id/status")
        .headers(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .body(corpo)
        .exchange()

    private fun login(email: String) = testClient.post().uri("/api/v1/auth")
        .contentType(MediaType.APPLICATION_JSON)
        .body(UsuarioLoginDto(email, JwtAuthentication.SENHA))
        .exchange()

    private val novoProdutor = ProdutorCreateDto(
        name = "Joana Lima",
        email = "joana@email.com",
        password = "123456",
        cnpj = "11.444.777/0001-61",
        companyName = "Granja da Joana",
        technicalResponsibleName = "Dr. Pedro Alves",
        shortDescription = "Ovos e petiscos de frango"
    )

    private fun cadastrarComDocumento(documento: ByteArray?, nome: String = "alvara.pdf") = testClient.post()
        .uri("/api/v1/usuarios/produtores")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(LinkedMultiValueMap<String, Any>().apply {
            // Cada parte tem o próprio Content-Type: "dados" é JSON, "documento" é o arquivo
            add("dados", HttpEntity(novoProdutor, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }))
            if (documento != null) {
                val parteHeaders = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_PDF
                    setContentDispositionFormData("documento", nome)
                }
                add("documento", HttpEntity(object : ByteArrayResource(documento) {
                    override fun getFilename() = nome
                }, parteHeaders))
            }
        })
        .exchange()

    // ---------- GET /produtores/pendentes ----------

    @Test
    fun listarPendentes_ComAdmin_RetornarSoPendentesComStatus200() {
        val pagina = testClient.get().uri("/api/v1/produtores/pendentes").headers(admin())
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody

        assertThat(pagina!!.ids()).containsExactly(104)
        @Suppress("UNCHECKED_CAST")
        val item = pagina.content[0] as Map<String, Any?>
        assertThat(item["companyName"]).isEqualTo("Horta da Paula")
        assertThat(item["email"]).isEqualTo("paula@email.com")
        assertThat(item["status"]).isEqualTo("pending")
        assertThat(item["document"]).isNull()
    }

    @Test
    fun listarPendentes_ComClienteOuSemToken_Retornar403e401() {
        esperarErro(testClient.get().uri("/api/v1/produtores/pendentes").headers(cliente()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/produtores/pendentes").exchange(), 401)
    }

    // ---------- GET /produtores/cadastros ----------

    @Test
    fun listarCadastros_PorStatus_RetornarFiltradoOuTodos() {
        fun ids(uri: String) = testClient.get().uri(uri).headers(admin())
            .exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!.ids()

        assertThat(ids("/api/v1/produtores/cadastros?status=rejected")).containsExactly(105)
        assertThat(ids("/api/v1/produtores/cadastros?status=approved")).containsExactlyInAnyOrder(103, 106)
        assertThat(ids("/api/v1/produtores/cadastros")).containsExactlyInAnyOrder(103, 104, 105, 106)
    }

    @Test
    fun listarCadastros_StatusInvalido_Retornar400() {
        esperarErro(testClient.get().uri("/api/v1/produtores/cadastros?status=xyz").headers(admin()).exchange(), 400)
    }

    // ---------- PATCH /produtores/{id}/status ----------

    @Test
    fun aprovar_Pendente_Retornar200ELiberarLoginECatalogo() {
        val dto = patchStatus(104, ProdutorStatusDto("approved"))
            .expectStatus().isOk
            .expectBody(ProdutorCadastroDto::class.java).returnResult().responseBody!!

        assertThat(dto.status).isEqualTo("approved")
        assertThat(dto.reviewedAt).isNotNull
        assertThat(dto.rejectionReason).isNull()
        login("paula@email.com").expectStatus().isOk.expectBody(JwtToken::class.java)
        testClient.get().uri("/api/v1/produtores/104").exchange().expectStatus().isOk
    }

    @Test
    fun rejeitar_SemMotivo_Retornar422ComErrorsReason() {
        val erro = esperarErro(patchStatus(104, ProdutorStatusDto("rejected", "  ")), 422)
        assertThat(erro.errors).containsKey("reason")
    }

    @Test
    fun rejeitar_ComMotivo_Retornar200EMotivoAparecerNoLogin() {
        val dto = patchStatus(104, ProdutorStatusDto("rejected", "Documento ilegível"))
            .expectStatus().isOk
            .expectBody(ProdutorCadastroDto::class.java).returnResult().responseBody!!
        assertThat(dto.status).isEqualTo("rejected")
        assertThat(dto.rejectionReason).isEqualTo("Documento ilegível")

        val erro = esperarErro(login("paula@email.com"), 403)
        assertThat(erro.message).isEqualTo("Cadastro de produtor rejeitado. Motivo: Documento ilegível")
    }

    @Test
    fun reabrir_Rejeitado_VoltarParaPendenteSemMotivo() {
        val dto = patchStatus(105, ProdutorStatusDto("pending"))
            .expectStatus().isOk
            .expectBody(ProdutorCadastroDto::class.java).returnResult().responseBody!!
        assertThat(dto.status).isEqualTo("pending")
        assertThat(dto.reviewedAt).isNull()
        assertThat(dto.rejectionReason).isNull()
    }

    @Test
    fun mudarStatus_TransicaoNaoPermitida_Retornar409() {
        // aprovado não muda mais; pendente não "reabre"; rejeitado não vai direto para aprovado
        esperarErro(patchStatus(103, ProdutorStatusDto("rejected", "x")), 409)
        esperarErro(patchStatus(104, ProdutorStatusDto("pending")), 409)
        val erro = esperarErro(patchStatus(105, ProdutorStatusDto("approved")), 409)
        assertThat(erro.message).contains("rejected para approved")
    }

    @Test
    fun mudarStatus_StatusInvalidoOuProdutorInexistente_Retornar422e404() {
        val erro = esperarErro(patchStatus(104, ProdutorStatusDto("aprovado")), 422)
        assertThat(erro.errors).containsKey("status")
        esperarErro(patchStatus(9999, ProdutorStatusDto("approved")), 404)
    }

    @Test
    fun mudarStatus_ComCliente_Retornar403() {
        esperarErro(
            testClient.patch().uri("/api/v1/produtores/104/status").headers(cliente())
                .contentType(MediaType.APPLICATION_JSON).body(ProdutorStatusDto("approved")).exchange(),
            403
        )
    }

    // ---------- Cadastro com documento e download ----------

    @Test
    fun cadastrarComDocumento_AdminVeEBaixaOArquivoIgual() {
        val conteudo = "qualquer formato serve: %PDF-falso".toByteArray()
        val criado = cadastrarComDocumento(conteudo)
            .expectStatus().isCreated
            .expectBody(ProdutorResponseDto::class.java).returnResult().responseBody!!
        assertThat(criado.status).isEqualTo("pending")

        val pagina = testClient.get().uri("/api/v1/produtores/pendentes").headers(admin())
            .exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
        @Suppress("UNCHECKED_CAST")
        val item = pagina.content.map { it as Map<String, Any?> }.first { it["id"] == criado.id }
        @Suppress("UNCHECKED_CAST")
        val documento = item["document"] as Map<String, Any?>
        assertThat(documento["name"]).isEqualTo("alvara.pdf")
        assertThat(documento["contentType"]).isEqualTo("application/pdf")
        assertThat((documento["size"] as Number).toLong()).isEqualTo(conteudo.size.toLong())

        val resposta = testClient.get().uri("/api/v1/produtores/${criado.id}/documento").headers(admin())
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_PDF)
            .expectBody(ByteArray::class.java).returnResult()
        assertThat(resposta.responseBody).isEqualTo(conteudo)
        assertThat(resposta.responseHeaders.getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("alvara.pdf")
    }

    @Test
    fun cadastrarComDocumento_SemArquivo_Retornar422ComErrorsDocumento() {
        val erro = esperarErro(cadastrarComDocumento(null), 422)
        assertThat(erro.errors).containsKey("documento")
        // Nada foi criado: o mesmo e-mail continua livre
        cadastrarComDocumento("ok".toByteArray()).expectStatus().isCreated
    }

    @Test
    fun cadastrarComDocumento_AcimaDe5MB_Retornar422() {
        val erro = esperarErro(cadastrarComDocumento(ByteArray(5 * 1024 * 1024 + 1)), 422)
        assertThat(erro.errors!!["documento"]).isEqualTo("deve ter no máximo 5 MB")
    }

    @Test
    fun baixarDocumento_ProdutorSemDocumentoOuComCliente_Retornar404e403() {
        esperarErro(testClient.get().uri("/api/v1/produtores/104/documento").headers(admin()).exchange(), 404)
        esperarErro(testClient.get().uri("/api/v1/produtores/104/documento").headers(cliente()).exchange(), 403)
    }
}
