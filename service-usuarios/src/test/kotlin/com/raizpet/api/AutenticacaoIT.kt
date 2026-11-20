package com.raizpet.api

import com.raizpet.api.jwt.JwtToken
import com.raizpet.api.web.dto.UsuarioLoginDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AutenticacaoIT {

    @Autowired
    lateinit var testClient: RestTestClient

    private fun login(email: String?, password: String?) = testClient
        .post()
        .uri("/api/v1/auth")
        .contentType(MediaType.APPLICATION_JSON)
        .body(UsuarioLoginDto(email, password))
        .exchange()

    private fun roleDoToken(token: String): String {
        val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
        return Regex(""""role":"(\w+)"""").find(payload)!!.groupValues[1]
    }

    private fun idDoToken(token: String): Int {
        val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
        return Regex(""""id":(\d+)""").find(payload)!!.groupValues[1].toInt()
    }

    // O service-produtos e o service-pedidos dependem dessa claim para saber quem é o usuário
    @Test
    fun autenticar_TokenTrazClaimIdDoUsuario() {
        mapOf("ana@email.com" to 100, "bia@email.com" to 101, "carlos@email.com" to 103).forEach { (email, id) ->
            val token = login(email, "123456")
                .expectStatus().isOk
                .expectBody(JwtToken::class.java)
                .returnResult().responseBody!!.token

            assertThat(idDoToken(token)).isEqualTo(id)
        }
    }

    private fun loginBloqueado(email: String, mensagem: String) {
        val responseBody = login(email, "123456")
            .expectStatus().isForbidden
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(403)
        assertThat(responseBody.message).isEqualTo(mensagem)
    }

    @Test
    fun autenticar_ComCredenciaisValidasDosTresPerfis_RetornarTokenComRoleEStatus200() {
        mapOf(
            "ana@email.com" to "ADMIN",
            "bia@email.com" to "CUSTOMER",
            "carlos@email.com" to "PRODUCER"
        ).forEach { (email, role) ->
            val responseBody = login(email, "123456")
                .expectStatus().isOk
                .expectBody(JwtToken::class.java)
                .returnResult().responseBody

            assertThat(responseBody).isNotNull
            assertThat(responseBody!!.token).isNotBlank()
            assertThat(roleDoToken(responseBody.token)).isEqualTo(role)
        }
    }

    @Test
    fun autenticar_ComCredenciaisInvalidas_RetornarErrorMessageStatus400() {
        var responseBody = login("invalido@email.com", "123456")
            .expectStatus().isBadRequest
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(400)

        responseBody = login("bia@email.com", "000000")
            .expectStatus().isBadRequest
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(400)
    }

    @Test
    fun autenticar_ComCamposInvalidos_RetornarErrorMessageStatus422() {
        listOf(
            null to "123456",
            "" to "123456",
            "@email.com" to "123456",
            "bia@email.com" to "",
            "bia@email.com" to "123",
            "bia@email.com" to null
        ).forEach { (email, password) ->
            val responseBody = login(email, password)
                .expectStatus().isEqualTo(422)
                .expectBody(ErrorMessage::class.java)
                .returnResult().responseBody

            assertThat(responseBody).isNotNull
            assertThat(responseBody!!.status).isEqualTo(422)
        }
    }

    @Test
    fun autenticar_ComProdutorPendente_RetornarErrorMessageStatus403() {
        loginBloqueado("paula@email.com", "Seu cadastro está em análise, aguarde aprovação")
    }

    @Test
    fun autenticar_ComProdutorRejeitado_RetornarErrorMessageStatus403() {
        loginBloqueado("rita@email.com", "Cadastro de produtor rejeitado")
    }

    @Test
    fun autenticar_ComClienteSuspenso_RetornarErrorMessageStatus403() {
        loginBloqueado("bob@email.com", "Conta suspensa")
    }

    @Test
    fun autenticar_ComProdutorPendenteESenhaErrada_RetornarStatus400() {
        // Bloqueio só é revelado para quem acertou a senha
        login("paula@email.com", "000000").expectStatus().isBadRequest
    }
}
