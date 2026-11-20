package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.INTERNO_SCHEME
import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.service.EstoqueService
import com.raizpet.api.service.ProductService
import com.raizpet.api.web.dto.EstoqueItensDto
import com.raizpet.api.web.dto.ProdutoInternoDto
import com.raizpet.api.web.dto.mapper.ProdutoMapper
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Rotas chamadas por OUTROS SERVIÇOS (service-pedidos), nunca pelo app.
 * Protegidas pelo header X-Internal-Key (InternoKeyFilter); token JWT de usuário não abre estas rotas.
 */
@Tag(name = "Interno", description = "Uso entre serviços (exige o header X-Internal-Key)")
@RestController
@RequestMapping("/api/v1/interno")
class InternoController(
    private val productService: ProductService,
    private val estoqueService: EstoqueService
) {

    companion object {
        private const val MAX_IDS = 100
    }

    @Operation(
        summary = "Buscar produtos por ids (service-pedidos)",
        description = "Ex.: ?ids=1,2,3. Devolve os que existirem, ativos ou não (campo active), ordenados por id; " +
            "id inexistente simplesmente não aparece. Não confere o produtor no service-usuarios.",
        security = [SecurityRequirement(name = INTERNO_SCHEME)],
        parameters = [Parameter(name = "ids", description = "Ids separados por vírgula (máx. 100)", required = true)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Produtos encontrados",
                content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = ProdutoInternoDto::class)))]
            ),
            ApiResponse(
                responseCode = "400", description = "ids ausente, vazio, não numérico ou acima de 100",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Header X-Internal-Key ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/produtos")
    fun getProdutosPorIds(@RequestParam(required = false) ids: String?): ResponseEntity<List<ProdutoInternoDto>> {
        val lista = ids?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { it.toIntOrNull() ?: throw ParametroInvalidoException("ids inválido: '$it' não é um número") }
            ?.distinct()
        if (lista.isNullOrEmpty()) throw ParametroInvalidoException("Informe ids, ex.: ?ids=1,2,3")
        if (lista.size > MAX_IDS) throw ParametroInvalidoException("No máximo $MAX_IDS ids por consulta")
        return ResponseEntity.ok(productService.buscarPorIds(lista).map(ProdutoMapper::toInternoDto))
    }

    @Operation(
        summary = "Reservar estoque (checkout do service-pedidos)",
        description = "Tudo ou nada, numa transação local: baixa o estoque de todos os itens ou de nenhum. " +
            "Produto repetido na lista tem as quantidades somadas.",
        security = [SecurityRequirement(name = INTERNO_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Reserva feita; devolve os produtos com o estoque já baixado",
                content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = ProdutoInternoDto::class)))]
            ),
            ApiResponse(
                responseCode = "401", description = "Header X-Internal-Key ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Algum produto não existe (nada foi baixado)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Estoque insuficiente ou produto inativo (nada foi baixado; errors = productId -> motivo)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Lista vazia, productId ausente ou quantity <= 0",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/estoque/reserva")
    fun reservar(@Valid @RequestBody dto: EstoqueItensDto): ResponseEntity<List<ProdutoInternoDto>> =
        ResponseEntity.ok(estoqueService.reservar(dto.itens!!).map(ProdutoMapper::toInternoDto))

    @Operation(
        summary = "Estornar estoque (compensação simplificada)",
        description = "Recebe a MESMA lista de itens da reserva e devolve as quantidades ao estoque, numa transação local. " +
            "Sem idempotência: enviar duas vezes devolve duas vezes.",
        security = [SecurityRequirement(name = INTERNO_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Estoque devolvido; devolve os produtos com o estoque atualizado",
                content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = ProdutoInternoDto::class)))]
            ),
            ApiResponse(
                responseCode = "401", description = "Header X-Internal-Key ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Algum produto não existe (nada foi devolvido)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Lista vazia, productId ausente ou quantity <= 0",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/estoque/estorno")
    fun estornar(@Valid @RequestBody dto: EstoqueItensDto): ResponseEntity<List<ProdutoInternoDto>> =
        ResponseEntity.ok(estoqueService.estornar(dto.itens!!).map(ProdutoMapper::toInternoDto))
}
