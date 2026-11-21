package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.entity.DeliveryStatus
import com.raizpet.api.service.PedidoAdminService
import com.raizpet.api.web.dto.ClienteComprasDto
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.PedidoAdminDto
import com.raizpet.api.web.dto.PedidosResumoDto
import com.raizpet.api.web.dto.StatusEntregaDto
import com.raizpet.api.web.dto.mapper.PageableMapper
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Rotas do ADMIN no service-pedidos. Ficam num controller próprio porque o PedidoController
 * inteiro é CUSTOMER (@PreAuthorize na classe).
 */
@Tag(name = "Pedidos (admin)", description = "Visão do ADMIN sobre os pedidos")
@RestController
@RequestMapping("/api/v1/pedidos/admin")
@PreAuthorize("hasRole('ADMIN')")
class PedidoAdminController(private val pedidoAdminService: PedidoAdminService) {

    @Operation(
        summary = "Compras por cliente",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Para a tela Clientes do admin: o app manda os ids da página que veio do service-usuarios. " +
            "orders = pedidos PAGOS; totalSpent = soma do total deles (itens + frete). " +
            "Um item por id, na ordem pedida; cliente sem pedido pago vem com 0.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(
                `in` = ParameterIn.QUERY, name = "ids", required = true,
                content = [Content(schema = Schema(type = "string", example = "101,102"))],
                description = "Ids dos clientes, separados por vírgula (até 100)"
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Resumo por cliente",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = ClienteComprasDto::class))
                )]
            ),
            ApiResponse(responseCode = "400", description = "ids vazio, não numérico ou com mais de 100", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/clientes")
    fun getComprasPorCliente(
        @Parameter(hidden = true) @RequestParam(required = false) ids: List<Int>?
    ): ResponseEntity<List<ClienteComprasDto>> =
        ResponseEntity.ok(pedidoAdminService.comprasPorCliente(ids.orEmpty()))

    // ================= Etapa 5.5 =================

    @Operation(
        summary = "Listar todos os pedidos",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. Mais recentes primeiro. " +
            "busca = número do pedido ou trecho do nome do cliente; status = pendentes | enviados | entregues | atrasados | cancelados. " +
            "customerName vem do service-usuarios (null se ele estiver fora do ar). content = lista de PedidoAdminDto.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(`in` = ParameterIn.QUERY, name = "busca", content = [Content(schema = Schema(type = "string"))]),
            Parameter(`in` = ParameterIn.QUERY, name = "status", content = [Content(schema = Schema(
                type = "string", allowableValues = ["pendentes", "enviados", "entregues", "atrasados", "cancelados"]))]),
            Parameter(`in` = ParameterIn.QUERY, name = "page", content = [Content(schema = Schema(type = "integer", defaultValue = "0"))]),
            Parameter(`in` = ParameterIn.QUERY, name = "size", content = [Content(schema = Schema(type = "integer", defaultValue = "10"))])
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página de pedidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "status inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping
    fun getPedidos(
        @RequestParam(required = false) busca: String?,
        @RequestParam(required = false) status: String?,
        @Parameter(hidden = true) @PageableDefault(size = 10) pageable: Pageable
    ): ResponseEntity<PageableDto> = ResponseEntity.ok(PageableMapper.toDto(pedidoAdminService.listar(busca, status, pageable)))

    @Operation(
        summary = "Resumo dos pedidos",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "last30Days = pedidos criados nos últimos 30 dias; late = pedidos atrasados (pagos há mais de 3 dias " +
            "com alguma parte ainda não enviada), de qualquer data.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Resumo",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidosResumoDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/resumo")
    fun getResumo(): ResponseEntity<PedidosResumoDto> = ResponseEntity.ok(pedidoAdminService.resumo())

    @Operation(
        summary = "Cancelar pedido",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. Só se nenhuma parte foi enviada. " +
            "Todas as partes viram canceled, o estoque volta (estorno no service-produtos) e o pagamento pago vira refunded.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Pedido cancelado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoAdminDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Pedido inexistente", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "409", description = "Já cancelado ou com parte já enviada/entregue", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "503", description = "service-produtos fora do ar (nada foi alterado)", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @PostMapping("/{id}/cancelamento")
    fun cancelar(@PathVariable id: Int): ResponseEntity<PedidoAdminDto> = ResponseEntity.ok(pedidoAdminService.cancelar(id))

    @Operation(
        summary = "Corrigir o status de entrega de uma parte",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. Correção manual: qualquer um dos 4 status " +
            "(pode voltar). grupoId = producers[].groupId. Parte cancelada ou pedido não pago -> 409.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Pedido com a parte corrigida",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoAdminDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Parte inexistente", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "409", description = "Parte cancelada ou pedido não pago", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "422", description = "status fora de pending, preparing, shipped, delivered", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @PatchMapping("/grupos/{grupoId}/status")
    fun corrigirStatus(@PathVariable grupoId: Int, @Valid @RequestBody dto: StatusEntregaDto): ResponseEntity<PedidoAdminDto> =
        ResponseEntity.ok(pedidoAdminService.corrigirStatus(grupoId, DeliveryStatus.valueOf(dto.status!!)))
}
