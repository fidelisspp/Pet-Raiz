package com.raizpet.api.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthorizationFilter(
    private val detailsService: JwtUserDetailsService,
    private val jwtUtils: JwtUtils
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthorizationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = request.getHeader(JwtUtils.JWT_AUTHORIZATION)

        if (token == null || !token.startsWith(JwtUtils.JWT_BEARER)) {
            log.info("JWT Token está nulo, vazio ou não iniciado com 'Bearer '.")
            filterChain.doFilter(request, response)
            return
        }

        val username = jwtUtils.getUsernameFromToken(token)
        if (username == null) {
            log.warn("JWT Token está inválido ou expirado.")
            filterChain.doFilter(request, response)
            return
        }

        toAuthentication(request, username)

        filterChain.doFilter(request, response)
    }

    private fun toAuthentication(request: HttpServletRequest, username: String) {
        val userDetails = try {
            detailsService.loadUserByUsername(username)
        } catch (ex: UsernameNotFoundException) {
            log.warn("Usuário do token não existe mais: {}", username)
            return
        }

        val authenticationToken = UsernamePasswordAuthenticationToken
            .authenticated(userDetails, null, userDetails.authorities)

        authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)

        SecurityContextHolder.getContext().authentication = authenticationToken
    }
}
