package com.raizpet.api.web.exception

import com.raizpet.api.exception.*
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import com.raizpet.api.service.DocumentoService

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

    // Transição de status não permitida (Etapa 5.1)
    @ExceptionHandler(StatusInvalidoException::class)
    fun statusInvalidoException(
        ex: StatusInvalidoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.CONFLICT)

    // Campo recusado por regra do serviço (motivo ausente, documento ausente/grande): 422 com errors.<campo>
    @ExceptionHandler(CampoInvalidoException::class)
    fun campoInvalidoException(
        ex: CampoInvalidoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Campo recusado: {} = {}", ex.campo, ex.message)
        return campoInvalido(request, ex.titulo, ex.campo, ex.message ?: "")
    }

    // Arquivo acima de spring.servlet.multipart.max-file-size: o Spring recusa antes de chegar ao controller
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun maxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Upload recusado: acima do limite ({})", ex.message)
        return campoInvalido(request, "Documento inválido", "documento", DocumentoService.MSG_TAMANHO)
    }

    // Ex.: cadastro enviado como text/plain em vez de JSON ou multipart
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun httpMediaTypeNotSupportedException(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Content-Type não suportado: {}", ex.contentType)
        return ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorMessage(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type não suportado: ${ex.contentType}"))
    }

    // Filtro de consulta inválido (ex.: ?status=xyz)
    @ExceptionHandler(ParametroInvalidoException::class)
    fun parametroInvalidoException(
        ex: ParametroInvalidoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.BAD_REQUEST)

    // Ex.: ?suspenso=talvez ou /clientes/abc (Etapa 5.3)
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun methodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Parâmetro inválido: {}={}", ex.name, ex.value)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorMessage(request, HttpStatus.BAD_REQUEST, "Parâmetro inválido: ${ex.name}=${ex.value}"))
    }

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

    private fun campoInvalido(
        request: HttpServletRequest,
        titulo: String,
        campo: String,
        motivo: String
    ): ResponseEntity<ErrorMessage> {
        val status = HttpStatus.UNPROCESSABLE_ENTITY
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ErrorMessage(
                    path = request.requestURI,
                    method = request.method,
                    status = status.value(),
                    statusText = status.reasonPhrase,
                    message = titulo,
                    errors = mapOf(campo to motivo)
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
