package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.service.ProductService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutosResumoDto
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
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Produtos na visão do ADMIN (Etapa 5.4). "Publicado" é o mesmo recorte do catálogo: ativo e de produtor
 * aprovado. Por isso, como o catálogo, depende do service-usuarios (fora do ar = 503).
 */
@Tag(name = "Produtos (admin)", description = "Produtos publicados na visão do ADMIN: busca, resumo e remoção")
@RestController
@RequestMapping("/api/v1/produtos/admin")
@PreAuthorize("hasRole('ADMIN')")
class ProdutoAdminController(private val productService: ProductService) {

    @Operation(
        summary = "Listar produtos publicados",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. Mesmo formato do catálogo " +
            "(content = ProdutoResponseDto). busca procura no nome do produto e no nome do produtor; " +
            "categoriaId filtra. Ordem padrão: nome.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(`in` = ParameterIn.QUERY, name = "busca", content = [Content(schema = Schema(type = "string"))],
                description = "Trecho do nome do produto ou do produtor (opcional)"),
            Parameter(`in` = ParameterIn.QUERY, name = "categoriaId", content = [Content(schema = Schema(type = "integer"))],
                description = "Categoria (opcional)"),
            Parameter(`in` = ParameterIn.QUERY, name = "page", content = [Content(schema = Schema(type = "integer", defaultValue = "0"))]),
            Parameter(`in` = ParameterIn.QUERY, name = "size", content = [Content(schema = Schema(type = "integer", defaultValue = "10"))])
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página de produtos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "categoriaId não numérico", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "503", description = "service-usuarios fora do ar", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping
    fun getAll(
        @RequestParam(required = false) busca: String?,
        @RequestParam(required = false) categoriaId: Int?,
        @Parameter(hidden = true) @PageableDefault(size = 10, sort = ["name"]) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val pagina = productService.buscarParaAdmin(busca, categoriaId, pageable)
            .map { ProdutoMapper.toDto(it.product, it.produtor) }
        return ResponseEntity.ok(PageableMapper.toDto(pagina))
    }

    @Operation(
        summary = "Resumo dos produtos publicados",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "products = produtos publicados; producers = produtores aprovados com pelo menos um publicado.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Resumo",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutosResumoDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "503", description = "service-usuarios fora do ar", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/resumo")
    fun getResumo(): ResponseEntity<ProdutosResumoDto> {
        val (produtos, produtores) = productService.resumoParaAdmin()
        return ResponseEntity.ok(ProdutosResumoDto(produtos, produtores))
    }

    @Operation(
        summary = "Remover produto da loja",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. Qualquer produto, de qualquer produtor: " +
            "o mesmo soft delete do produtor (some do catálogo e do /meus; pedidos antigos não mudam). Não depende do service-usuarios.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Removido"),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Produto inexistente ou já removido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Int): ResponseEntity<Void> {
        productService.removerComoAdmin(id)
        return ResponseEntity.noContent().build()
    }
}
