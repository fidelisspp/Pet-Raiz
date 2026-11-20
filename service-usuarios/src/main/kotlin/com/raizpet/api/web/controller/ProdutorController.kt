package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.jwt.JwtUserDetails
import com.raizpet.api.service.ProducerService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutorMeDto
import com.raizpet.api.web.dto.ProdutorPublicoDto
import com.raizpet.api.web.dto.mapper.PageableMapper
import com.raizpet.api.web.dto.mapper.ProdutorMapper
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Vitrine pública dos produtores (home do app, filtro "Produtor" e página do produtor).
 * Não confundir com POST /api/v1/usuarios/produtores, que é o cadastro.
 */
@Tag(name = "Produtores", description = "Dados públicos dos produtores aprovados")
@RestController
@RequestMapping("/api/v1/produtores")
class ProdutorController(private val producerService: ProducerService) {

    @Operation(
        summary = "Listar produtores aprovados",
        description = "Recurso público e paginado. Produtor pendente ou rejeitado não aparece.",
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
                content = [Content(schema = Schema(type = "string", defaultValue = "companyName,asc"))],
                description = "Ordenação"
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página de produtores (content = lista de ProdutorPublicoDto)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            )
        ]
    )
    @GetMapping
    fun getAll(
        @Parameter(hidden = true) @PageableDefault(size = 10, sort = ["companyName"]) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val produtores = producerService.buscarAprovados(pageable).map(ProdutorMapper::toPublicoDto)
        return ResponseEntity.ok(PageableMapper.toDto(produtores))
    }

    @Operation(
        summary = "Meu perfil de produtor",
        description = "Requisição exige um Bearer Token. Acesso restrito a PRODUCER. " +
            "Dados do produtor do token, para a área do produtor no app. " +
            "imageUrl é o caminho relativo (/images/x.jpg), servido pelo service-produtos.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Perfil do produtor logado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutorMeDto::class))]
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
    @GetMapping("/me")
    @PreAuthorize("hasRole('PRODUCER')")
    fun getMe(@AuthenticationPrincipal userDetails: JwtUserDetails): ResponseEntity<ProdutorMeDto> =
        ResponseEntity.ok(ProdutorMapper.toMeDto(producerService.buscarPorId(userDetails.id)))

    @Operation(
        summary = "Localizar um produtor aprovado", description = "Recurso público.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Produtor localizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutorPublicoDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Produtor inexistente, pendente ou rejeitado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Int): ResponseEntity<ProdutorPublicoDto> =
        ResponseEntity.ok(ProdutorMapper.toPublicoDto(producerService.buscarAprovadoPorId(id)))
}
