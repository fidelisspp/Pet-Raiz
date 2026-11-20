package com.raizpet.api.config

import com.raizpet.api.web.exception.ErrorMessage
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Protege as rotas que começam com /api/v1/interno/, que só outros serviços chamam.
 * (Não escreva o curinga de duas estrelas aqui: barra + duas estrelas abre um comentário aninhado em Kotlin.)
 *
 * - Header X-Internal-Key igual a petraiz.interno.chave: a requisição vira o "usuário" SERVICE
 *   (ROLE_SERVICE), que é o único perfil liberado nessas rotas no SpringSecurityConfig.
 * - Header ausente ou errado: responde 401 aqui mesmo, no formato ErrorMessage.
 * - Qualquer outra rota: o filtro não faz nada (shouldNotFilter).
 *
 * Roda DEPOIS do JwtAuthorizationFilter, então mesmo que alguém mande um Bearer token de usuário
 * junto, o que vale nas rotas internas é a chave.
 */
class InternoKeyFilter(
    chave: String,
    private val jsonMapper: JsonMapper
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Internal-Key"
        const val PREFIXO = "/api/v1/interno/"
        const val ROLE = "SERVICE"
        private val log = LoggerFactory.getLogger(InternoKeyFilter::class.java)
    }

    private val chaveBytes = chave.toByteArray(StandardCharsets.UTF_8)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith(PREFIXO)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val enviada = request.getHeader(HEADER)
        // MessageDigest.isEqual compara em tempo constante (não "vaza" quantos caracteres acertou)
        if (enviada == null || !MessageDigest.isEqual(enviada.toByteArray(StandardCharsets.UTF_8), chaveBytes)) {
            log.warn("Chamada interna recusada em {}: header {} ausente ou inválido", request.requestURI, HEADER)
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            jsonMapper.writeValue(
                response.outputStream,
                ErrorMessage(request, HttpStatus.UNAUTHORIZED, "Rota interna: envie o header $HEADER válido")
            )
            return
        }

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken
            .authenticated("service", null, AuthorityUtils.createAuthorityList("ROLE_$ROLE"))
        filterChain.doFilter(request, response)
    }
}
