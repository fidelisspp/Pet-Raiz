package com.raizpet.api

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.http.HttpHeaders
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.function.Consumer

/**
 * Este serviço não tem POST /auth: nos testes o token é gerado aqui, do mesmo jeito que o
 * JwtUtils.createToken do service-usuarios (HS256, claims sub, role e id).
 * O teste com o token emitido de verdade pela 8081 está no roteiro curl do relatório.
 */
object Tokens {

    // Mesmo valor padrão de petraiz.jwt.secret-key
    const val SECRET = "0123456789-0123456789-0123456789"

    fun criar(
        id: Int?,
        role: String?,
        email: String = "teste@email.com",
        expiraEmMs: Long = 60 * 60 * 1000,
        secret: String = SECRET
    ): String {
        val agora = Date()
        val builder = Jwts.builder()
            .header().add("typ", "JWT").and()
            .subject(email)
            .issuedAt(agora)
            .expiration(Date(agora.time + expiraEmMs))
        if (role != null) builder.claim("role", role)
        if (id != null) builder.claim("id", id)
        return builder
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact()
    }

    fun header(token: String): Consumer<HttpHeaders> =
        Consumer { it.add(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    // Usuários do seed do service-usuarios
    fun adminAna() = header(criar(100, "ADMIN", "ana@email.com"))
    fun clienteBia() = header(criar(101, "CUSTOMER", "bia@email.com"))
    fun clienteBobSuspenso() = header(criar(102, "CUSTOMER", "bob@email.com"))
    fun produtorCarlos() = header(criar(103, "PRODUCER", "carlos@email.com"))

    // Token válido de CUSTOMER cujo id não existe no service-usuarios
    fun clienteInexistente() = header(criar(999, "CUSTOMER", "fantasma@email.com"))
}
