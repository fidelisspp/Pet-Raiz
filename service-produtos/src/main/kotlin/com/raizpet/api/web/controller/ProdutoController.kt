package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.entity.PetType
import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.ProductService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutoCreateDto
import com.raizpet.api.web.dto.ProdutoCriadoResponseDto
import com.raizpet.api.web.dto.ProdutoResponseDto
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
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Produtos", description = "Catálogo de produtos (listagem pública) e cadastro pelo produtor")
@RestController
@RequestMapping("/api/v1/produtos")
class ProdutoController(private val productService: ProductService) {

    @Operation(
        summary = "Listar o catálogo",
        description = "Recurso público e paginado. Só retorna produtos ativos de produtores aprovados " +
            "(lista consultada no service-usuarios a cada requisição). " +
            "Filtros opcionais: nome (contém, sem diferenciar maiúsculas), categoriaId, produtorId e petType " +
            "(dog = dog + both; cat = cat + both; both = só both).",
        parameters = [
            Parameter(`in` = ParameterIn.QUERY, name = "nome", description = "Trecho do nome do produto"),
            Parameter(`in` = ParameterIn.QUERY, name = "categoriaId", description = "Id da categoria"),
            Parameter(`in` = ParameterIn.QUERY, name = "produtorId", description = "Id do produtor (usuário no service-usuarios)"),
            Parameter(
                `in` = ParameterIn.QUERY, name = "petType", description = "Tipo de pet",
                content = [Content(schema = Schema(type = "string", allowableValues = ["dog", "cat", "both"]))]
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
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "sort",
                content = [Content(schema = Schema(type = "string", defaultValue = "name,asc"))],
                description = "Ordenação, ex.: price,desc"
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página do catálogo (content = lista de ProdutoResponseDto)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "petType inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-usuarios indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    fun getCatalogo(
        @RequestParam(required = false) nome: String?,
        @RequestParam(required = false) categoriaId: Int?,
        @RequestParam(required = false) produtorId: Int?,
        @RequestParam(required = false) petType: String?,
        @Parameter(hidden = true) @PageableDefault(size = 10, sort = ["name"]) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val tipo = PetType.deTexto(petType)
        if (tipo == null && !petType.isNullOrBlank()) {
            throw ParametroInvalidoException("petType inválido: '$petType'. Use dog, cat ou both")
        }
        val produtos = productService.buscarCatalogo(nome, categoriaId, produtorId, tipo, pageable)
            .map { ProdutoMapper.toDto(it.product, it.produtor) }
        return ResponseEntity.ok(PageableMapper.toDto(produtos))
    }

    @Operation(
        summary = "Localizar um produto do catálogo", description = "Recurso público.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Produto localizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produto inexistente, inativo ou de produtor não aprovado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "503", description = "service-usuarios indisponível",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Int): ResponseEntity<ProdutoResponseDto> {
        val item = productService.buscarNoCatalogoPorId(id)
        return ResponseEntity.ok(ProdutoMapper.toDto(item.product, item.produtor))
    }

    @Operation(
        summary = "Cadastrar produto",
        description = "Requisição exige um Bearer Token emitido pelo service-usuarios. Acesso restrito a PRODUCER. " +
            "O produtor dono é o id do token (validado só pelas claims, sem consultar outro serviço). " +
            "imageUrl é o caminho relativo /images/arquivo.jpg.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "201", description = "Produto criado com sucesso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutoCriadoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Recurso não permitido ao perfil",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Categoria não encontrada",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Recurso não processado por dados de entrada invalidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping
    @PreAuthorize("hasRole('PRODUCER')")
    fun create(
        @Valid @RequestBody dto: ProdutoCreateDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<ProdutoCriadoResponseDto> {
        val product = productService.salvar(ProdutoMapper.toProduct(dto), usuario.id, dto.categoryId!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(ProdutoMapper.toCriadoDto(product))
    }
}
