package com.raizpet.api.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Diferente do service-usuarios: aqui NÃO há loadUserByUsername (não existe banco de usuários).
 * Token válido vira autenticação com o JwtUsuario das claims e a authority ROLE_<role>.
 * Token ausente/inválido: segue sem autenticação (rota pública passa; protegida dá 401 no entry point).
 */
class JwtAuthorizationFilter(private val jwtUtils: JwtUtils) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthorizationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = request.getHeader(JwtUtils.JWT_AUTHORIZATION)

        if (token == null || !token.startsWith(JwtUtils.JWT_BEARER)) {
            filterChain.doFilter(request, response)
            return
        }

        val usuario = jwtUtils.getUsuarioFromToken(token)
        if (usuario == null) {
            log.warn("JWT Token está inválido ou expirado.")
            filterChain.doFilter(request, response)
            return
        }

        val authenticationToken = UsernamePasswordAuthenticationToken
            .authenticated(usuario, null, AuthorityUtils.createAuthorityList("ROLE_" + usuario.role))
        authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authenticationToken

        filterChain.doFilter(request, response)
    }
}
