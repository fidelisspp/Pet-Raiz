package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.service.CustomerService
import com.raizpet.api.web.dto.ClienteResponseDto
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.mapper.ClienteMapper
import com.raizpet.api.web.dto.mapper.PageableMapper
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Clientes", description = "Consulta de clientes (restrito a ADMIN)")
@RestController
@RequestMapping("/api/v1/clientes")
class ClienteController(private val customerService: CustomerService) {

    @Operation(
        summary = "Localizar um cliente", description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Recurso localizado com sucesso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ClienteResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Recurso não permitido ao perfil",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Cliente não encontrado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getById(@PathVariable id: Int): ResponseEntity<ClienteResponseDto> =
        ResponseEntity.ok(ClienteMapper.toDto(customerService.buscarPorId(id)))

    @Operation(
        summary = "Recuperar lista de clientes", description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(
                `in` = ParameterIn.QUERY, name = "page",
                content = [Content(schema = Schema(type = "integer", defaultValue = "0"))],
                description = "Representa a página retornada"
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "size",
                content = [Content(schema = Schema(type = "integer", defaultValue = "5"))],
                description = "Representa o total de elementos por página"
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "sort", hidden = true,
                content = [Content(schema = Schema(type = "string", defaultValue = "name,asc"))],
                description = "Representa a ordenação dos resultados"
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Recurso recuperado com sucesso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Recurso não permitido ao perfil",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getAll(@Parameter(hidden = true) @PageableDefault(size = 5, sort = ["name"]) pageable: Pageable): ResponseEntity<PageableDto> {
        val clientes = customerService.buscarTodos(pageable).map(ClienteMapper::toDto)
        return ResponseEntity.ok(PageableMapper.toDto(clientes))
    }
}
