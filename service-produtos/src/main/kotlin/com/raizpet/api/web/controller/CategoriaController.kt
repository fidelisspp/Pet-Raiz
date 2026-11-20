package com.raizpet.api.web.controller

import com.raizpet.api.service.CategoryService
import com.raizpet.api.web.dto.CategoriaResponseDto
import com.raizpet.api.web.dto.mapper.CategoriaMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Categorias", description = "Categorias do catálogo de produtos")
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
}
