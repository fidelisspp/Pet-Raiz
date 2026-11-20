package com.raizpet.api.web.controller

import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PatchMapping
import jakarta.validation.Valid
import com.raizpet.api.web.dto.ClienteSuspensaoDto
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

@Tag(name = "Clientes", description = "Consulta, busca e suspensão de clientes (restrito a ADMIN)")
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
        summary = "Recuperar lista de clientes",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Filtros opcionais (Etapa 5.3): busca (trecho do nome ou do e-mail) e suspenso (true/false).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(
                `in` = ParameterIn.QUERY, name = "busca",
                content = [Content(schema = Schema(type = "string"))],
                description = "Trecho do nome ou do e-mail (opcional)"
            ),
            Parameter(
                `in` = ParameterIn.QUERY, name = "suspenso",
                content = [Content(schema = Schema(type = "boolean"))],
                description = "true = só suspensos; false = só ativos; ausente = todos"
            ),
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
    fun getAll(
        @RequestParam(required = false) busca: String?,
        @RequestParam(required = false) suspenso: Boolean?,
        @Parameter(hidden = true) @PageableDefault(size = 5, sort = ["name"]) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val clientes = customerService.buscarParaAdmin(busca, suspenso, pageable).map(ClienteMapper::toDto)
        return ResponseEntity.ok(PageableMapper.toDto(clientes))
    }

    @Operation(
        summary = "Suspender ou reativar cliente",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "{suspended: true} suspende (o cliente não faz login nem finaliza compras); false reativa. Idempotente.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Cliente com o novo status",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ClienteResponseDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "422", description = "suspended ausente", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @PatchMapping("/{id}/suspensao")
    @PreAuthorize("hasRole('ADMIN')")
    fun patchSuspensao(@PathVariable id: Int, @Valid @RequestBody dto: ClienteSuspensaoDto): ResponseEntity<ClienteResponseDto> =
        ResponseEntity.ok(ClienteMapper.toDto(customerService.alterarSuspensao(id, dto.suspended!!)))
}
