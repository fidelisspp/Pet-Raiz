package com.raizpet.api.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtils(
    @Value("\${petraiz.jwt.secret-key}") secretKey: String,
    @Value("\${petraiz.jwt.expire-days}") private val expireDays: Long,
    @Value("\${petraiz.jwt.expire-hours}") private val expireHours: Long,
    @Value("\${petraiz.jwt.expire-minutes}") private val expireMinutes: Long
) {

    companion object {
        const val JWT_BEARER = "Bearer "
        const val JWT_AUTHORIZATION = "Authorization"
        const val CLAIM_ID = "id"
        private val log = LoggerFactory.getLogger(JwtUtils::class.java)
    }

    private val key: SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray(StandardCharsets.UTF_8))

    private fun toExpireDate(start: Date): Date {
        val dateTime = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        val end: LocalDateTime = dateTime.plusDays(expireDays).plusHours(expireHours).plusMinutes(expireMinutes)
        return Date.from(end.atZone(ZoneId.systemDefault()).toInstant())
    }

    /**
     * Claims do token: sub (e-mail), iat, exp, role e id.
     * O id existe para o service-produtos e o service-pedidos, que NÃO têm tabela de usuário:
     * eles leem id e role direto do token (validado com a mesma secret-key) para saber
     * quem é o produtor dono do produto ou o cliente dono do carrinho.
     */
    fun createToken(username: String, role: String, id: Int): JwtToken {
        val issuedAt = Date()
        val limit = toExpireDate(issuedAt)

        val token = Jwts.builder()
            .header().add("typ", "JWT").and()
            .subject(username)
            .issuedAt(issuedAt)
            .expiration(limit)
            .signWith(key, Jwts.SIG.HS256)
            .claim("role", role)
            .claim(CLAIM_ID, id)
            .compact()

        return JwtToken(token)
    }

    private fun getClaimsFromToken(token: String): Claims? =
        try {
            Jwts.parser()
                .verifyWith(key).build()
                .parseSignedClaims(refactorToken(token)).payload
        } catch (ex: JwtException) {
            log.error("Token invalido {}", ex.message)
            null
        }

    fun getUsernameFromToken(token: String): String? = getClaimsFromToken(token)?.subject

    fun isTokenValid(token: String): Boolean = getClaimsFromToken(token) != null

    private fun refactorToken(token: String): String =
        if (token.contains(JWT_BEARER)) token.substring(JWT_BEARER.length) else token
}
