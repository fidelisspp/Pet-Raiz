package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.entity.DeliveryStatus
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.PedidoRecebidoService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.PedidoRecebidoResponseDto
import com.raizpet.api.web.dto.StatusEntregaDto
import com.raizpet.api.web.dto.mapper.PageableMapper
import com.raizpet.api.web.dto.mapper.PedidoMapper
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * Área do produtor: os pedidos que ele recebeu. Cada item é a PARTE dele num pedido pago
 * (order_producer_group); o {grupoId} das rotas é o id dessa parte.
 * O GET /pedidos do cliente mostra o mesmo status em producers[].deliveryStatus.
 */
@Tag(name = "Pedidos recebidos", description = "Produtor acompanha e avança os pedidos que recebeu (perfil PRODUCER)")
@RestController
@RequestMapping("/api/v1/pedidos/recebidos")
@PreAuthorize("hasRole('PRODUCER')")
class PedidoRecebidoController(private val pedidoRecebidoService: PedidoRecebidoService) {

    @Operation(
        summary = "Listar pedidos recebidos",
        description = "Requisição exige um Bearer Token de PRODUCER. Só pedidos pagos; mais recentes primeiro. " +
            "Cada item é a parte do produtor no pedido: itens dele, total dele, destinatário e endereço " +
            "(content = lista de PedidoRecebidoResponseDto).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(
                `in` = ParameterIn.QUERY, name = "status", description = "Filtra pelo status de entrega (opcional)",
                content = [Content(schema = Schema(type = "string", allowableValues = ["pending", "preparing", "shipped", "delivered"]))]
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "page",
                content = [Content(schema = Schema(type = "integer", defaultValue = "0"))],
                description = "Representa a página retornada"
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "size",
                content = [Content(schema = Schema(type = "integer", defaultValue = "10"))],
                description = "Representa o total de elementos por página"
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página de pedidos recebidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "status inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de PRODUCER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    fun listar(
        @AuthenticationPrincipal usuario: JwtUsuario,
        @Parameter(hidden = true) @RequestParam(required = false) status: DeliveryStatus?,
        @Parameter(hidden = true) @PageableDefault(size = 10) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val pagina = pedidoRecebidoService.listar(usuario.id, status, pageable).map(PedidoMapper::toRecebidoDto)
        return ResponseEntity.ok(PageableMapper.toDto(pagina))
    }

    @Operation(
        summary = "Ver um pedido recebido",
        description = "Requisição exige um Bearer Token de PRODUCER. grupoId = id do item da lista.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Pedido recebido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoRecebidoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de PRODUCER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Inexistente, de outro produtor ou de pedido ainda não pago",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{grupoId}")
    fun buscar(
        @PathVariable grupoId: Int,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<PedidoRecebidoResponseDto> =
        ResponseEntity.ok(PedidoMapper.toRecebidoDto(pedidoRecebidoService.buscar(grupoId, usuario.id)))

    @Operation(
        summary = "Avançar o status de entrega",
        description = "Requisição exige um Bearer Token de PRODUCER. Só avança uma etapa por vez: " +
            "pending -> preparing -> shipped -> delivered. Voltar, pular ou repetir o status atual = 409. " +
            "O campo nextStatus da resposta diz qual status é aceito agora.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Status alterado; devolve o pedido recebido atualizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoRecebidoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de PRODUCER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Inexistente, de outro produtor ou de pedido ainda não pago",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Transição não permitida (voltar, pular ou repetir)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "status ausente ou fora de pending|preparing|shipped|delivered",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PatchMapping("/{grupoId}/status")
    fun avancarStatus(
        @PathVariable grupoId: Int,
        @Valid @RequestBody dto: StatusEntregaDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<PedidoRecebidoResponseDto> {
        val group = pedidoRecebidoService.avancarStatus(grupoId, usuario.id, DeliveryStatus.valueOf(dto.status!!))
        return ResponseEntity.ok(PedidoMapper.toRecebidoDto(group))
    }
}
