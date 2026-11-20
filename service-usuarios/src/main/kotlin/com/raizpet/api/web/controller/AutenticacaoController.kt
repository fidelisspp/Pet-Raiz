package com.raizpet.api.web.controller

import com.raizpet.api.jwt.JwtToken
import com.raizpet.api.jwt.JwtUserDetailsService
import com.raizpet.api.web.dto.UsuarioLoginDto
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Autenticação", description = "Recurso para proceder com a autenticação da API")
@RestController
@RequestMapping("/api/v1")
class AutenticacaoController(
    private val detailsService: JwtUserDetailsService,
    private val authenticationManager: AuthenticationManager
) {

    private val log = LoggerFactory.getLogger(AutenticacaoController::class.java)

    @Operation(
        summary = "Autenticar na API",
        description = "Login por e-mail e senha. Retorna um bearer token com o perfil (CUSTOMER, PRODUCER ou ADMIN).",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Autenticação realizada com sucesso e retorno de um bearer token",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = JwtToken::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Credenciais inválidas",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Produtor em análise ou rejeitado, ou cliente suspenso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Campo(s) Inválido(s)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/auth")
    fun autenticar(@RequestBody @Valid dto: UsuarioLoginDto, request: HttpServletRequest): ResponseEntity<Any> {
        val email = dto.email!!
        log.info("Processo de autenticação pelo login {}", email)
        try {
            val authenticationToken = UsernamePasswordAuthenticationToken(email, dto.password)
            authenticationManager.authenticate(authenticationToken)
        } catch (ex: AuthenticationException) {
            log.warn("Bad Credentials from username '{}'", email)
            return ResponseEntity
                .badRequest()
                .body(ErrorMessage(request, HttpStatus.BAD_REQUEST, "Credenciais Inválidas"))
        }
        // Senha correta: agora valem as regras de bloqueio (AccountBlockedException -> 403)
        val token = detailsService.getTokenAuthenticated(email)
        return ResponseEntity.ok(token)
    }
}
