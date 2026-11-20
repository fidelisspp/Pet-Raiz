package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.ProductService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutoCreateDto
import com.raizpet.api.web.dto.ProdutoCriadoResponseDto
import com.raizpet.api.web.dto.mapper.PageableMapper
import com.raizpet.api.web.dto.mapper.ProdutoMapper
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
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * Área do produtor: os produtos do dono do token (claim id), inclusive com estoque 0.
 * Fica separado do ProdutoController porque o catálogo é público e filtra produtor aprovado;
 * aqui é o contrário: só o dono, sem consultar o service-usuarios.
 * Produto de outro produtor ou já removido = 404.
 */
@Tag(name = "Meus produtos", description = "Produtor gerencia os próprios produtos (perfil PRODUCER)")
@RestController
@RequestMapping("/api/v1/produtos/meus")
@PreAuthorize("hasRole('PRODUCER')")
class ProdutoMeuController(private val productService: ProductService) {

    @Operation(
        summary = "Listar meus produtos",
        description = "Requisição exige um Bearer Token de PRODUCER. Paginado, mais recentes primeiro. " +
            "Inclui produtos com estoque 0; não inclui os removidos. " +
            "content = lista de ProdutoCriadoResponseDto.",
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
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "sort",
                content = [Content(schema = Schema(type = "string", defaultValue = "createdAt,desc"))],
                description = "Ordenação, ex.: name,asc"
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página com os produtos do produtor",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
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
        @Parameter(hidden = true)
        @PageableDefault(size = 10, sort = ["createdAt", "id"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val produtos = productService.buscarMeus(usuario.id, pageable).map(ProdutoMapper::toCriadoDto)
        return ResponseEntity.ok(PageableMapper.toDto(produtos))
    }

    @Operation(
        summary = "Ver um produto meu",
        description = "Requisição exige um Bearer Token de PRODUCER.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Produto localizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutoCriadoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de PRODUCER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto inexistente, removido ou de outro produtor",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<ProdutoCriadoResponseDto> =
        ResponseEntity.ok(ProdutoMapper.toCriadoDto(productService.buscarMeu(id, usuario.id)))

    @Operation(
        summary = "Alterar um produto meu",
        description = "Requisição exige um Bearer Token de PRODUCER. Mesmo corpo do POST /produtos. " +
            "imageUrl e petType são opcionais: se não vierem, o produto mantém a foto e o tipo atuais. " +
            "Pedidos já feitos não mudam (o item do pedido guarda nome, preço e imagem da época).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Produto alterado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutoCriadoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de PRODUCER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto inexistente, removido ou de outro produtor; ou categoria inexistente",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Dados de entrada inválidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: Int,
        @Valid @RequestBody dto: ProdutoCreateDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<ProdutoCriadoResponseDto> {
        val product = productService.atualizarMeu(id, usuario.id, dto.categoryId!!) { ProdutoMapper.atualizar(it, dto) }
        return ResponseEntity.ok(ProdutoMapper.toCriadoDto(product))
    }

    @Operation(
        summary = "Remover um produto meu",
        description = "Requisição exige um Bearer Token de PRODUCER. Soft delete: o produto fica inativo, some do " +
            "catálogo e desta lista, mas continua no banco (pedidos antigos não são afetados).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Produto removido", content = [Content()]),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de PRODUCER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto inexistente, já removido ou de outro produtor",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @DeleteMapping("/{id}")
    fun remover(@PathVariable id: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<Void> {
        productService.removerMeu(id, usuario.id)
        return ResponseEntity.noContent().build()
    }
}
