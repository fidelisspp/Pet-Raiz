package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.service.PedidoAdminService
import com.raizpet.api.web.dto.ClienteComprasDto
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
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
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
}
