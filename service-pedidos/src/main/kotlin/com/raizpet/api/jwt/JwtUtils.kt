package com.raizpet.api.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

/**
 * Só VALIDA token: quem emite é o service-usuarios (POST /api/v1/auth na 8081).
 * Assinatura HS256 com a mesma petraiz.jwt.secret-key; a expiração (exp) é conferida pelo jjwt.
 * Não existe tabela de usuário aqui: o "usuário" é o que as claims id e role dizem.
 */
@Component
class JwtUtils(@Value("\${petraiz.jwt.secret-key}") secretKey: String) {

    companion object {
        const val JWT_BEARER = "Bearer "
        const val JWT_AUTHORIZATION = "Authorization"
        const val CLAIM_ID = "id"
        const val CLAIM_ROLE = "role"
        private val ROLES = setOf("CUSTOMER", "PRODUCER", "ADMIN")
        private val log = LoggerFactory.getLogger(JwtUtils::class.java)
    }

    private val key: SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray(StandardCharsets.UTF_8))

    private fun getClaimsFromToken(token: String): Claims? =
        try {
            Jwts.parser()
                .verifyWith(key).build()
                .parseSignedClaims(refactorToken(token)).payload
        } catch (ex: JwtException) {
            log.error("Token invalido {}", ex.message)
            null
        } catch (ex: IllegalArgumentException) {
            log.error("Token invalido {}", ex.message)
            null
        }

    /** Token válido E com as claims id (inteiro) e role (perfil conhecido); senão null. */
    fun getUsuarioFromToken(token: String): JwtUsuario? {
        val claims = getClaimsFromToken(token) ?: return null
        val id = (claims[CLAIM_ID] as? Number)?.toInt()
        val role = claims[CLAIM_ROLE] as? String
        if (id == null || role == null || role !in ROLES) {
            log.warn("Token sem as claims id/role esperadas (sub={})", claims.subject)
            return null
        }
        return JwtUsuario(id = id, email = claims.subject ?: "", role = role)
    }

    private fun refactorToken(token: String): String =
        if (token.startsWith(JWT_BEARER)) token.substring(JWT_BEARER.length) else token
}
