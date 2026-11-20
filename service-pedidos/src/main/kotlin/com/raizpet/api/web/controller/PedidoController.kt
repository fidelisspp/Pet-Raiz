package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.entity.PaymentMethod
import com.raizpet.api.entity.ShippingMethod
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.CheckoutService
import com.raizpet.api.service.PedidoService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.PedidoCreateDto
import com.raizpet.api.web.dto.PedidoResponseDto
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
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Pedidos", description = "Checkout, pagamento simulado e histórico do cliente logado (perfil CUSTOMER)")
@RestController
@RequestMapping("/api/v1/pedidos")
@PreAuthorize("hasRole('CUSTOMER')")
class PedidoController(
    private val checkoutService: CheckoutService,
    private val pedidoService: PedidoService
) {

    @Operation(
        summary = "Finalizar o carrinho (checkout)",
        description = "Compra o carrinho inteiro. Frete por pedido: standard = 12.90, express = 24.90. " +
            "Sequência: confere endereço e carrinho -> confere no service-usuarios se o cliente está suspenso (403) -> " +
            "reserva o estoque no service-produtos (tudo ou nada; 409 deixa o carrinho intacto) -> grava o pedido " +
            "(itens agrupados por produtor, preço/nome/imagem copiados, pagamento pending) e esvazia o carrinho. " +
            "Se a gravação falhar, o estoque é estornado (500).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "201", description = "Pedido criado; devolve o pedido completo",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de CUSTOMER, ou cliente suspenso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Endereço inexistente ou de outro cliente",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Reserva recusada: estoque insuficiente, produto inativo ou inexistente (nada foi baixado; errors = productId -> motivo)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Campos inválidos ou carrinho vazio",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "500", description = "Pedido não gravado depois da reserva (estoque estornado)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-usuarios ou service-produtos indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping
    fun finalizar(
        @Valid @RequestBody dto: PedidoCreateDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<PedidoResponseDto> {
        val id = checkoutService.finalizar(
            usuario.id,
            dto.addressId!!,
            ShippingMethod.valueOf(dto.shippingMethod!!),
            PaymentMethod.deApi(dto.paymentMethod)!!
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoMapper.toDto(pedidoService.buscarDoCliente(id, usuario.id)))
    }

    @Operation(
        summary = "Confirmar o pagamento (simulado)",
        description = "Sem gateway por enquanto: muda o pagamento de pending para paid e grava confirmedAt.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Pagamento confirmado; devolve o pedido completo",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Pedido inexistente ou de outro cliente",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Pagamento já confirmado (ou não está pending)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/{id}/pagamento")
    fun confirmarPagamento(@PathVariable id: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<PedidoResponseDto> =
        ResponseEntity.ok(PedidoMapper.toDto(pedidoService.confirmarPagamento(id, usuario.id)))

    @Operation(
        summary = "Meu histórico de pedidos",
        description = "Paginado, mais recentes primeiro. Cada item é o pedido completo (content = lista de PedidoResponseDto).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
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
                responseCode = "200", description = "Página de pedidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de CUSTOMER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    fun historico(
        @AuthenticationPrincipal usuario: JwtUsuario,
        @Parameter(hidden = true)
        @PageableDefault(size = 10, sort = ["createdAt", "id"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<PageableDto> =
        ResponseEntity.ok(PageableMapper.toDto(pedidoService.historico(usuario.id, pageable).map(PedidoMapper::toDto)))

    @Operation(
        summary = "Ver um pedido meu",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Pedido completo",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PedidoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Pedido inexistente ou de outro cliente",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<PedidoResponseDto> =
        ResponseEntity.ok(PedidoMapper.toDto(pedidoService.buscarDoCliente(id, usuario.id)))
}
