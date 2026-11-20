package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.FavoritoService
import com.raizpet.api.web.dto.FavoritoCreateDto
import com.raizpet.api.web.dto.FavoritoResponseDto
import com.raizpet.api.web.dto.mapper.FavoritoMapper
import com.raizpet.api.web.exception.ErrorMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Favoritos", description = "Produtos favoritos do cliente logado (perfil CUSTOMER). Dados dos produtos vêm do service-produtos")
@RestController
@RequestMapping("/api/v1/favoritos")
@PreAuthorize("hasRole('CUSTOMER')")
class FavoritoController(private val favoritoService: FavoritoService) {

    @Operation(
        summary = "Listar meus favoritos",
        description = "Mais recentes primeiro, sem paginação. Nome, preço, estoque e imagem de cada produto vêm do " +
            "service-produtos numa única chamada. Produto removido continua na lista com available = false.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Lista de favoritos (pode ser vazia)",
                content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = FavoritoResponseDto::class)))]
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
    fun listar(@AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<List<FavoritoResponseDto>> =
        ResponseEntity.ok(favoritoService.listar(usuario.id).map(FavoritoMapper::toDto))

    @Operation(
        summary = "Favoritar um produto",
        description = "Idempotente: 201 quando o favorito é criado; 200 (mesmo corpo) quando o produto já era favorito.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "201", description = "Favorito criado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = FavoritoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "200", description = "Produto já era favorito (nada mudou)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = FavoritoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto não existe",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Produto inativo (errors = productId -> motivo)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "productId ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-produtos indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping
    fun adicionar(
        @Valid @RequestBody dto: FavoritoCreateDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<FavoritoResponseDto> {
        val resultado = favoritoService.adicionar(usuario.id, dto.productId!!)
        val status = if (resultado.criado) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(FavoritoMapper.toDto(resultado.favorito))
    }

    @Operation(
        summary = "Desfavoritar um produto",
        description = "Idempotente: 204 mesmo se o produto não era favorito. Não chama o service-produtos.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Removido (ou já não era favorito)", content = [Content()]),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de CUSTOMER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @DeleteMapping("/{productId}")
    fun remover(@PathVariable productId: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<Void> {
        favoritoService.remover(usuario.id, productId)
        return ResponseEntity.noContent().build()
    }
}
