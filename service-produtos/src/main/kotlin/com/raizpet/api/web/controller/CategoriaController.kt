package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.service.CategoryService
import com.raizpet.api.web.dto.CategoriaCreateDto
import com.raizpet.api.web.dto.CategoriaResponseDto
import com.raizpet.api.web.dto.CategoriaResumoDto
import com.raizpet.api.web.dto.mapper.CategoriaMapper
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Categorias", description = "Categorias do catálogo de produtos (leitura pública; gestão pelo ADMIN)")
@RestController
@RequestMapping("/api/v1/categorias")
class CategoriaController(private val categoryService: CategoryService) {

    @Operation(
        summary = "Listar categorias",
        description = "Recurso público. Lista todas as categorias em ordem alfabética. Não depende do service-usuarios.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Lista de categorias",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = CategoriaResponseDto::class))
                )]
            )
        ]
    )
    @GetMapping
    fun getAll(): ResponseEntity<List<CategoriaResponseDto>> =
        ResponseEntity.ok(CategoriaMapper.toListDto(categoryService.buscarTodas()))

    // ================= Gestão pelo ADMIN — Etapa 5.2 =================

    @Operation(
        summary = "Categorias com a quantidade de produtos",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. Ordem alfabética. " +
            "productCount = produtos ativos; removedProductCount = removidos pelo produtor (também impedem excluir).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Lista de categorias com contagem",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = CategoriaResumoDto::class))
                )]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/resumo")
    @PreAuthorize("hasRole('ADMIN')")
    fun getResumo(): ResponseEntity<List<CategoriaResumoDto>> {
        val contagens = categoryService.contagens()
        return ResponseEntity.ok(categoryService.buscarTodas().map { CategoriaMapper.toResumoDto(it, contagens) })
    }

    @Operation(
        summary = "Criar categoria",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Nome único sem diferenciar maiúsculas/minúsculas nem acentos.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "201", description = "Categoria criada",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CategoriaResponseDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "409", description = "Já existe uma categoria com esse nome", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "422", description = "Nome vazio ou com mais de 100 caracteres", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@Valid @RequestBody dto: CategoriaCreateDto): ResponseEntity<CategoriaResponseDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(CategoriaMapper.toDto(categoryService.criar(dto.name!!)))

    @Operation(
        summary = "Renomear categoria",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Os produtos guardam o id da categoria: o nome novo aparece no catálogo na hora.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Categoria renomeada",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CategoriaResponseDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Categoria inexistente", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "409", description = "Já existe outra categoria com esse nome", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "422", description = "Nome vazio ou com mais de 100 caracteres", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(@PathVariable id: Int, @Valid @RequestBody dto: CategoriaCreateDto): ResponseEntity<CategoriaResponseDto> =
        ResponseEntity.ok(CategoriaMapper.toDto(categoryService.renomear(id, dto.name!!)))

    @Operation(
        summary = "Excluir categoria",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Só categoria sem nenhum produto, nem removido (o pedido antigo ainda aponta para ele): senão 409.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Categoria excluída"),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Categoria inexistente", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "409", description = "Categoria tem produtos (ativos ou removidos)", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: Int): ResponseEntity<Void> {
        categoryService.excluir(id)
        return ResponseEntity.noContent().build()
    }
}
