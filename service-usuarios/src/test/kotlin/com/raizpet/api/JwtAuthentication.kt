package com.raizpet.api

import com.raizpet.api.jwt.JwtToken
import com.raizpet.api.web.dto.UsuarioLoginDto
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.function.Consumer

object JwtAuthentication {

    // Senha padrão de todos os usuários do seed
    const val SENHA = "123456"

    fun getHeaderAuthorization(client: RestTestClient, email: String, password: String = SENHA): Consumer<HttpHeaders> {
        val token = client
            .post()
            .uri("/api/v1/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .body(UsuarioLoginDto(email, password))
            .exchange()
            .expectStatus().isOk
            .expectBody(JwtToken::class.java)
            .returnResult().responseBody!!.token
        return Consumer { headers -> headers.add(HttpHeaders.AUTHORIZATION, "Bearer $token") }
    }
}
