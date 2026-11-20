package com.raizpet.api

import com.raizpet.api.web.dto.*
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UsuarioIT {

    @Autowired
    lateinit var testClient: RestTestClient

    private fun clienteValido() = ClienteCreateDto(
        name = "Carla Cliente",
        email = "carla@email.com",
        phone = "(11)95555-0000",
        password = "123456",
        cpf = "52998224725"
    )

    private fun produtorValido() = ProdutorCreateDto(
        name = "Pedro Produtor",
        email = "pedro@email.com",
        phone = null,
        password = "123456",
        cnpj = "67439015000121",
        companyName = "Quintal do Pedro",
        technicalResponsibleName = "Dr. Pedro Alves",
        shortDescription = "Ração crua de carne bovina orgânica"
    )

    private fun postCliente(dto: ClienteCreateDto) = testClient
        .post()
        .uri("/api/v1/usuarios/clientes")
        .contentType(MediaType.APPLICATION_JSON)
        .body(dto)
        .exchange()

    private fun postProdutor(dto: ProdutorCreateDto) = testClient
        .post()
        .uri("/api/v1/usuarios/produtores")
        .contentType(MediaType.APPLICATION_JSON)
        .body(dto)
        .exchange()

    private fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int): ErrorMessage {
        val responseBody = spec
            .expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(status)
        return responseBody
    }

    // ---------- POST /usuarios/clientes ----------

    @Test
    fun createCliente_ComDadosValidos_RetornarClienteCriadoComStatus201() {
        val responseBody = postCliente(clienteValido())
            .expectStatus().isCreated
            .expectBody(ClienteResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.id).isPositive()
        assertThat(responseBody.email).isEqualTo("carla@email.com")
        assertThat(responseBody.cpf).isEqualTo("529.982.247-25")
        assertThat(responseBody.suspended).isFalse()

        // Cliente recém-criado já consegue logar como CUSTOMER
        JwtAuthentication.getHeaderAuthorization(testClient, "carla@email.com")
    }

    @Test
    fun createCliente_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        listOf(
            clienteValido().copy(name = null),
            clienteValido().copy(name = "Ana"),
            clienteValido().copy(email = "carla@"),
            clienteValido().copy(email = "carla@email"),
            clienteValido().copy(password = "12345"),
            clienteValido().copy(password = null),
            clienteValido().copy(cpf = "00000000000"),
            clienteValido().copy(cpf = "123")
        ).forEach { esperarErro(postCliente(it), 422) }

        val responseBody = esperarErro(postCliente(ClienteCreateDto()), 422)
        assertThat(responseBody.errors).containsKeys("name", "email", "password", "cpf")
    }

    @Test
    fun createCliente_ComEmailOuCpfRepetido_RetornarErrorMessageStatus409() {
        esperarErro(postCliente(clienteValido().copy(email = "bia@email.com")), 409)
        // CPF do seed enviado sem máscara: a normalização detecta o duplicado
        esperarErro(postCliente(clienteValido().copy(cpf = "79074426050")), 409)
        esperarErro(postCliente(clienteValido().copy(cpf = "790.744.260-50")), 409)
    }

    // ---------- POST /usuarios/produtores ----------

    @Test
    fun createProdutor_ComDadosValidos_RetornarProdutorPendenteComStatus201() {
        val responseBody = postProdutor(produtorValido())
            .expectStatus().isCreated
            .expectBody(ProdutorResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.id).isPositive()
        assertThat(responseBody.cnpj).isEqualTo("67.439.015/0001-21")
        assertThat(responseBody.status).isEqualTo("pending")

        // Produtor recém-criado fica bloqueado até aprovação
        testClient.post().uri("/api/v1/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .body(UsuarioLoginDto("pedro@email.com", "123456"))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun createProdutor_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        listOf(
            produtorValido().copy(cnpj = "11111111111111"),
            produtorValido().copy(cnpj = null),
            produtorValido().copy(companyName = ""),
            produtorValido().copy(companyName = "x".repeat(201)),
            produtorValido().copy(technicalResponsibleName = null),
            produtorValido().copy(shortDescription = "x".repeat(501)),
            produtorValido().copy(email = "pedro"),
            produtorValido().copy(password = "123")
        ).forEach { esperarErro(postProdutor(it), 422) }
    }

    @Test
    fun createProdutor_ComEmailOuCnpjRepetido_RetornarErrorMessageStatus409() {
        esperarErro(postProdutor(produtorValido().copy(email = "carlos@email.com")), 409)
        esperarErro(postProdutor(produtorValido().copy(cnpj = "11222333000181")), 409)
    }

    // ---------- GET /usuarios/me ----------

    @Test
    fun buscarMe_ComUsuarioAutenticado_RetornarDadosDoLogadoComStatus200() {
        mapOf(
            "ana@email.com" to "ADMIN",
            "bia@email.com" to "CUSTOMER",
            "carlos@email.com" to "PRODUCER"
        ).forEach { (email, role) ->
            val responseBody = testClient
                .get()
                .uri("/api/v1/usuarios/me")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, email))
                .exchange()
                .expectStatus().isOk
                .expectBody(UsuarioResponseDto::class.java)
                .returnResult().responseBody

            assertThat(responseBody).isNotNull
            assertThat(responseBody!!.email).isEqualTo(email)
            assertThat(responseBody.role).isEqualTo(role)
        }
    }

    @Test
    fun buscarMe_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/usuarios/me").exchange(), 401)
    }

    // ---------- GET /usuarios/{id} ----------

    @Test
    fun buscarUsuario_ComAdminOuProprioUsuario_RetornarUsuarioComStatus200() {
        var responseBody = testClient
            .get()
            .uri("/api/v1/usuarios/101")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(UsuarioResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.id).isEqualTo(101)
        assertThat(responseBody.role).isEqualTo("CUSTOMER")

        responseBody = testClient
            .get()
            .uri("/api/v1/usuarios/103")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "carlos@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(UsuarioResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.id).isEqualTo(103)
        assertThat(responseBody.role).isEqualTo("PRODUCER")
    }

    @Test
    fun buscarUsuario_NuncaExpoePasswordHash() {
        val json = testClient
            .get()
            .uri("/api/v1/usuarios/101")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "bia@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult().responseBody

        assertThat(json).doesNotContain("password").doesNotContain("\$2a\$")
    }

    @Test
    fun buscarUsuario_ComIdInexistente_RetornarErrorMessageStatus404() {
        esperarErro(
            testClient.get().uri("/api/v1/usuarios/0")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
                .exchange(),
            404
        )
    }

    @Test
    fun buscarUsuario_ComUsuarioBuscandoOutroUsuario_RetornarErrorMessageStatus403() {
        esperarErro(
            testClient.get().uri("/api/v1/usuarios/100")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "bia@email.com"))
                .exchange(),
            403
        )
        esperarErro(
            testClient.get().uri("/api/v1/usuarios/101")
                .headers(JwtAuthentication.getHeaderAuthorization(testClient, "carlos@email.com"))
                .exchange(),
            403
        )
    }

    @Test
    fun buscarUsuario_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/usuarios/101").exchange(), 401)
    }

    // ---------- PATCH /usuarios/{id} ----------

    private fun patchSenha(id: Int, email: String, dto: UsuarioSenhaDto) = testClient
        .patch()
        .uri("/api/v1/usuarios/$id")
        .headers(JwtAuthentication.getHeaderAuthorization(testClient, email))
        .contentType(MediaType.APPLICATION_JSON)
        .body(dto)
        .exchange()

    @Test
    fun editarSenha_ComDadosValidos_RetornarStatus204() {
        patchSenha(101, "bia@email.com", UsuarioSenhaDto("123456", "novaSenha1", "novaSenha1"))
            .expectStatus().isNoContent

        // A nova senha passa a valer no login
        JwtAuthentication.getHeaderAuthorization(testClient, "bia@email.com", "novaSenha1")

        patchSenha(103, "carlos@email.com", UsuarioSenhaDto("123456", "123456", "123456"))
            .expectStatus().isNoContent
    }

    @Test
    fun editarSenha_ComCamposInvalidos_RetornarErrorMessageStatus422() {
        listOf(
            UsuarioSenhaDto("", "", ""),
            UsuarioSenhaDto("12345", "12345", "12345"),
            UsuarioSenhaDto(null, "654321", "654321")
        ).forEach { esperarErro(patchSenha(101, "bia@email.com", it), 422) }
    }

    @Test
    fun editarSenha_ComSenhasInvalidas_RetornarErrorMessageStatus400() {
        // Confirmação diferente da nova senha
        esperarErro(patchSenha(101, "bia@email.com", UsuarioSenhaDto("123456", "654321", "000000")), 400)
        // Senha atual errada
        esperarErro(patchSenha(101, "bia@email.com", UsuarioSenhaDto("000000", "654321", "654321")), 400)
    }

    @Test
    fun editarSenha_DeOutroUsuario_RetornarErrorMessageStatus403() {
        // Nem o ADMIN altera a senha de outro usuário
        esperarErro(patchSenha(101, "ana@email.com", UsuarioSenhaDto("123456", "654321", "654321")), 403)
        esperarErro(patchSenha(100, "bia@email.com", UsuarioSenhaDto("123456", "654321", "654321")), 403)
    }

    @Test
    fun editarSenha_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(
            testClient.patch().uri("/api/v1/usuarios/101")
                .contentType(MediaType.APPLICATION_JSON)
                .body(UsuarioSenhaDto("123456", "654321", "654321"))
                .exchange(),
            401
        )
    }

    // ---------- GET /usuarios ----------

    @Test
    fun listarUsuarios_ComAdmin_RetornarTodosComStatus200() {
        val responseBody = testClient
            .get()
            .uri("/api/v1/usuarios")
            .headers(JwtAuthentication.getHeaderAuthorization(testClient, "ana@email.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<UsuarioResponseDto>>() {})
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        // 7 no seed-insert.sql: o produtor 106 entrou para testar os produtores públicos
        assertThat(responseBody!!).hasSize(7)
        assertThat(responseBody.map { it.role }).contains("ADMIN", "CUSTOMER", "PRODUCER")
    }

    @Test
    fun listarUsuarios_ComClienteOuProdutor_RetornarErrorMessageStatus403() {
        listOf("bia@email.com", "carlos@email.com").forEach { email ->
            esperarErro(
                testClient.get().uri("/api/v1/usuarios")
                    .headers(JwtAuthentication.getHeaderAuthorization(testClient, email))
                    .exchange(),
                403
            )
        }
    }

    @Test
    fun listarUsuarios_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/usuarios").exchange(), 401)
    }
}
