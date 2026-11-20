package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.CarrinhoService
import com.raizpet.api.web.dto.CarrinhoItemCreateDto
import com.raizpet.api.web.dto.CarrinhoItemUpdateDto
import com.raizpet.api.web.dto.CarrinhoResponseDto
import com.raizpet.api.web.dto.mapper.CarrinhoMapper
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Carrinho", description = "Carrinho do cliente logado (perfil CUSTOMER). Dados dos produtos vêm do service-produtos")
@RestController
@RequestMapping("/api/v1/carrinho")
@PreAuthorize("hasRole('CUSTOMER')")
class CarrinhoController(private val carrinhoService: CarrinhoService) {

    @Operation(
        summary = "Ver meu carrinho",
        description = "Criado vazio no primeiro acesso. Nome, preço, estoque, imagem e producerId de cada item vêm de " +
            "GET /api/v1/interno/produtos no service-produtos (uma chamada por requisição). subtotal não inclui frete.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Carrinho",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CarrinhoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de CUSTOMER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-produtos indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    fun ver(@AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<CarrinhoResponseDto> =
        ResponseEntity.ok(CarrinhoMapper.toDto(carrinhoService.ver(usuario.id)))

    @Operation(
        summary = "Adicionar produto ao carrinho",
        description = "Se o produto já estiver no carrinho, a quantidade é SOMADA. Confere o estoque atual " +
            "(não reserva: a reserva acontece só no checkout). Devolve o carrinho atualizado.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Carrinho atualizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CarrinhoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto não existe",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Quantidade total passa do estoque, ou produto inativo (errors = productId -> motivo)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "productId ausente ou quantity fora de 1..10000",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-produtos indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/itens")
    fun adicionar(
        @Valid @RequestBody dto: CarrinhoItemCreateDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<CarrinhoResponseDto> =
        ResponseEntity.ok(CarrinhoMapper.toDto(carrinhoService.adicionar(usuario.id, dto.productId!!, dto.quantity!!)))

    @Operation(
        summary = "Alterar a quantidade de um item",
        description = "quantity é o novo valor do item (não soma). Para tirar o item, use o DELETE.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Carrinho atualizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CarrinhoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto não está no carrinho (ou não existe mais)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Quantidade passa do estoque, ou produto inativo",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "quantity fora de 1..10000",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-produtos indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PatchMapping("/itens/{productId}")
    fun alterar(
        @PathVariable productId: Int,
        @Valid @RequestBody dto: CarrinhoItemUpdateDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<CarrinhoResponseDto> =
        ResponseEntity.ok(CarrinhoMapper.toDto(carrinhoService.alterar(usuario.id, productId, dto.quantity!!)))

    @Operation(
        summary = "Remover um item do carrinho",
        description = "Não chama o service-produtos.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Item removido"),
            ApiResponse(
                responseCode = "404", description = "Produto não está no carrinho",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @DeleteMapping("/itens/{productId}")
    fun remover(@PathVariable productId: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<Void> {
        carrinhoService.remover(usuario.id, productId)
        return ResponseEntity.noContent().build()
    }
}
