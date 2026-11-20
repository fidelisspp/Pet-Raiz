package com.raizpet.api.web.exception

import org.springframework.dao.DataIntegrityViolationException
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.ArquivoInvalidoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.exception.EstoqueInsuficienteException
import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.exception.ServicoIndisponivelException
import com.raizpet.api.service.ImagemService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun entityNotFoundException(
        ex: EntityNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.NOT_FOUND)

    // Upload recusado: 422 com errors.arquivo, no mesmo formato da validação de campos
    @ExceptionHandler(ArquivoInvalidoException::class)
    fun arquivoInvalidoException(
        ex: ArquivoInvalidoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Upload recusado: {}", ex.message)
        return arquivoInvalido(request, ex.message ?: ImagemService.MSG_TIPO)
    }

    // Arquivo acima de spring.servlet.multipart.max-file-size: o Spring recusa antes de chegar ao controller
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun maxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Upload recusado: acima do limite ({})", ex.message)
        return arquivoInvalido(request, ImagemService.MSG_TAMANHO)
    }

    // Ex.: upload enviado como JSON em vez de multipart/form-data
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun httpMediaTypeNotSupportedException(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Content-Type não suportado: {}", ex.contentType)
        return body(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type não suportado: ${ex.contentType}")
    }

    // Imagem (ou rota) inexistente: 404 no mesmo formato, sem stack trace no log
    @ExceptionHandler(NoResourceFoundException::class)
    fun noResourceFoundException(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Recurso não encontrado: {}", request.requestURI)
        return body(request, HttpStatus.NOT_FOUND, "Recurso não encontrado")
    }

    // Também cobre o 403 do @PreAuthorize (perfil errado)
    @ExceptionHandler(AccessDeniedException::class)
    fun accessDeniedException(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.FORBIDDEN)

    // Reserva tudo ou nada: errors = productId -> motivo
    @ExceptionHandler(EstoqueInsuficienteException::class)
    fun estoqueInsuficienteException(
        ex: EstoqueInsuficienteException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.warn("Api Error - {} {}", ex.message, ex.itens)
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ErrorMessage(
                    path = request.requestURI,
                    method = request.method,
                    status = HttpStatus.CONFLICT.value(),
                    statusText = HttpStatus.CONFLICT.reasonPhrase,
                    message = ex.message,
                    errors = ex.itens
                )
            )
    }

    // service-usuarios fora do ar / com erro: o motivo técnico fica só no log do UsuariosClient
    @ExceptionHandler(ServicoIndisponivelException::class)
    fun servicoIndisponivelException(
        ex: ServicoIndisponivelException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.warn("Api Error - 503 {}", ex.message)
        return body(request, HttpStatus.SERVICE_UNAVAILABLE, ex.message)
    }

    // Regra que impede a operação (Etapa 5.2: categoria repetida ou com produtos)
    @ExceptionHandler(ConflitoException::class)
    fun conflitoException(
        ex: ConflitoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Conflito: {}", ex.message)
        return body(request, HttpStatus.CONFLICT, ex.message)
    }

    // UNIQUE do banco (ex.: duas categorias criadas ao mesmo tempo com o mesmo nome)
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun dataIntegrityViolationException(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.warn("Violação de integridade: {}", ex.mostSpecificCause.message)
        return body(request, HttpStatus.CONFLICT, "A operação conflita com dados já existentes")
    }

    @ExceptionHandler(ParametroInvalidoException::class)
    fun parametroInvalidoException(
        ex: ParametroInvalidoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.BAD_REQUEST)

    // Ex.: categoriaId=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun methodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Parâmetro inválido: {}={}", ex.name, ex.value)
        return body(request, HttpStatus.BAD_REQUEST, "Parâmetro inválido: ${ex.name}=${ex.value}")
    }

    // JSON malformado no corpo
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun httpMessageNotReadableException(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Corpo ilegível: {}", ex.message)
        return body(request, HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou malformado")
    }

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

    private fun arquivoInvalido(request: HttpServletRequest, motivo: String): ResponseEntity<ErrorMessage> {
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
                    message = "Arquivo inválido",
                    errors = mapOf("arquivo" to motivo)
                )
            )
    }

    private fun error(ex: Exception, request: HttpServletRequest, status: HttpStatus): ResponseEntity<ErrorMessage> {
        log.error("Api Error - ", ex)
        return body(request, status, ex.message)
    }

    private fun body(request: HttpServletRequest, status: HttpStatus, message: String?): ResponseEntity<ErrorMessage> =
        ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorMessage(request, status, message))
}
