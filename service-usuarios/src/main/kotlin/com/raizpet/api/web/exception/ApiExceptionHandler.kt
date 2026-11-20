package com.raizpet.api.web.exception

import com.raizpet.api.exception.*
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun entityNotFoundException(
        ex: EntityNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.NOT_FOUND)

    // Também cobre o 403 do @PreAuthorize (perfil errado ou recurso de outro usuário)
    @ExceptionHandler(AccessDeniedException::class)
    fun accessDeniedException(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.FORBIDDEN)

    // Login bloqueado: produtor em análise/rejeitado ou cliente suspenso
    @ExceptionHandler(AccountBlockedException::class)
    fun accountBlockedException(
        ex: AccountBlockedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.FORBIDDEN)

    @ExceptionHandler(
        EmailUniqueViolationException::class,
        CpfUniqueViolationException::class,
        CnpjUniqueViolationException::class
    )
    fun uniqueViolationException(
        ex: RuntimeException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.CONFLICT)

    @ExceptionHandler(PasswordInvalidException::class)
    fun passwordInvalidException(
        ex: PasswordInvalidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.BAD_REQUEST)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun methodArgumentNotValidException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.error("Api Error - ", ex)
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ErrorMessage(
                    request,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Campo(s) invalido(s)",
                    ex.bindingResult
                )
            )
    }

    private fun error(ex: Exception, request: HttpServletRequest, status: HttpStatus): ResponseEntity<ErrorMessage> {
        log.error("Api Error - ", ex)
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorMessage(request, status, ex.message))
    }
}
