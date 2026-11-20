package com.raizpet.api.jwt

import com.raizpet.api.web.exception.ErrorMessage
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.json.JsonMapper

class JwtAuthenticationEntryPoint(private val jsonMapper: JsonMapper) : AuthenticationEntryPoint {

    private val log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint::class.java)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        log.info("Http Status 401 {}", authException.message)
        response.setHeader("www-authenticate", "Bearer realm='/api/v1/auth'")
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        val body = ErrorMessage(request, HttpStatus.UNAUTHORIZED, "Autenticação necessária: envie um Bearer token válido")
        jsonMapper.writeValue(response.outputStream, body)
    }
}
