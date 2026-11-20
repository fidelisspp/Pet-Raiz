package com.raizpet.api.web.exception

import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.exception.CarrinhoVazioException
import com.raizpet.api.exception.ClienteBloqueadoException
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.exception.PedidoNaoCriadoException
import com.raizpet.api.exception.ServicoIndisponivelException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun entityNotFoundException(
        ex: EntityNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> = error(ex, request, HttpStatus.NOT_FOUND)

    // Rota inexistente: 404 no mesmo formato, sem stack trace no log
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

    // Cliente suspenso no service-usuarios
    @ExceptionHandler(ClienteBloqueadoException::class)
    fun clienteBloqueadoException(
        ex: ClienteBloqueadoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.warn("Api Error - 403 {}", ex.message)
        return body(request, HttpStatus.FORBIDDEN, ex.message)
    }

    // Estoque, produto inativo, reserva recusada, endereço em uso, pagamento já confirmado.
    // errors (quando houver) = productId -> motivo
    @ExceptionHandler(ConflitoException::class)
    fun conflitoException(
        ex: ConflitoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.warn("Api Error - 409 {} {}", ex.message, ex.itens ?: "")
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

    @ExceptionHandler(CarrinhoVazioException::class)
    fun carrinhoVazioException(
        ex: CarrinhoVazioException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Api Error - 422 {}", ex.message)
        return body(request, HttpStatus.UNPROCESSABLE_ENTITY, ex.message)
    }

    // service-produtos ou service-usuarios fora do ar / com erro: o motivo técnico fica só no log do client
    @ExceptionHandler(ServicoIndisponivelException::class)
    fun servicoIndisponivelException(
        ex: ServicoIndisponivelException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.warn("Api Error - 503 {}", ex.message)
        return body(request, HttpStatus.SERVICE_UNAVAILABLE, ex.message)
    }

    // Transação local do checkout falhou depois da reserva (o estorno já foi tentado; detalhes no log)
    @ExceptionHandler(PedidoNaoCriadoException::class)
    fun pedidoNaoCriadoException(
        ex: PedidoNaoCriadoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.error("Api Error - 500 {}", ex.message)
        return body(request, HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
    }

    // Ex.: ?ids= vazio ou com mais de 100 (Etapa 5.3)
    @ExceptionHandler(ParametroInvalidoException::class)
    fun parametroInvalidoException(
        ex: ParametroInvalidoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorMessage> {
        log.info("Parâmetro inválido: {}", ex.message)
        return body(request, HttpStatus.BAD_REQUEST, ex.message)
    }

    // Ex.: /pedidos/abc
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
