package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.INTERNO_SCHEME
import com.raizpet.api.service.CustomerService
import com.raizpet.api.service.ProducerService
import com.raizpet.api.web.dto.ClienteInternoDto
import com.raizpet.api.web.dto.ProdutorAprovadoDto
import com.raizpet.api.web.dto.mapper.ProdutorMapper
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Rotas chamadas por OUTROS SERVIÇOS (service-produtos e service-pedidos), nunca pelo app.
 * Protegidas pelo header X-Internal-Key (InternoKeyFilter); token JWT de usuário não abre estas rotas.
 */
@Tag(name = "Interno", description = "Uso entre serviços (exige o header X-Internal-Key)")
@RestController
@RequestMapping("/api/v1/interno")
class InternoController(
    private val producerService: ProducerService,
    private val customerService: CustomerService
) {

    @Operation(
        summary = "Listar todos os produtores aprovados (service-produtos)",
        description = "Sem paginação. O service-produtos chama uma vez por consulta ao catálogo: usa os ids " +
            "para filtrar producer_id IN (...) e o companyName/cnpj para montar o produtor de cada produto.",
        security = [SecurityRequirement(name = INTERNO_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Lista de produtores aprovados, ordenada por id",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = ProdutorAprovadoDto::class))
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Header X-Internal-Key ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/produtores/aprovados")
    fun getProdutoresAprovados(): ResponseEntity<List<ProdutorAprovadoDto>> =
        ResponseEntity.ok(producerService.buscarTodosAprovados().map(ProdutorMapper::toAprovadoDto))

    @Operation(
        summary = "Situação de um cliente (service-pedidos)",
        description = "O service-pedidos chama no checkout, antes de reservar o estoque: suspended = true -> pedido recusado (403). " +
            "Só id e suspended: nenhum dado pessoal.",
        security = [SecurityRequirement(name = INTERNO_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Cliente encontrado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ClienteInternoDto::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Header X-Internal-Key ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Não existe cliente com esse id (inclusive se o id for de produtor ou admin)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/clientes/{id}")
    fun getCliente(@PathVariable id: Int): ResponseEntity<ClienteInternoDto> {
        val customer = customerService.buscarPorId(id)
        return ResponseEntity.ok(ClienteInternoDto(id = requireNotNull(customer.id), suspended = customer.suspended))
    }
}
