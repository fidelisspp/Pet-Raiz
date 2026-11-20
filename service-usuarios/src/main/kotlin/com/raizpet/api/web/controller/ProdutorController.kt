package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.entity.ProducerStatus
import com.raizpet.api.exception.CampoInvalidoException
import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.jwt.JwtUserDetails
import com.raizpet.api.service.ProducerService
import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutorCadastroDto
import com.raizpet.api.web.dto.ProdutorMeDto
import com.raizpet.api.web.dto.ProdutorPublicoDto
import com.raizpet.api.web.dto.ProdutorStatusDto
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
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

/**
 * Vitrine pública dos produtores (home do app, filtro "Produtor" e página do produtor) e o perfil do
 * produtor logado (GET /me). Não confundir com POST /api/v1/usuarios/produtores, que é o cadastro.
 */
@Tag(
    name = "Produtores",
    description = "Dados públicos dos produtores aprovados, perfil do produtor logado (/me) e análise de cadastros pelo ADMIN"
)
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

    // ================= Análise de cadastros (ADMIN) — Etapa 5.1 =================

    private fun paginaDeCadastros(status: ProducerStatus?, pageable: Pageable): PageableDto {
        val pagina = producerService.buscarCadastros(status, pageable)
            .map { ProdutorMapper.toCadastroDto(it, producerService.buscarDocumento(requireNotNull(it.id))) }
        return PageableMapper.toDto(pagina)
    }

    @Operation(
        summary = "Cadastros de produtor em análise",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Produtores com status 'pending', mais antigos primeiro (fila de análise). " +
            "content = lista de ProdutorCadastroDto.",
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
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página de cadastros pendentes",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/pendentes")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPendentes(
        @Parameter(hidden = true)
        @PageableDefault(size = 10, sort = ["producerCreatedAt"], direction = Sort.Direction.ASC) pageable: Pageable
    ): ResponseEntity<PageableDto> = ResponseEntity.ok(paginaDeCadastros(ProducerStatus.pending, pageable))

    @Operation(
        summary = "Cadastros de produtor por status",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Para as abas Aprovados e Rejeitados da tela de análise. Sem 'status', traz todos. " +
            "Mais recentes primeiro. content = lista de ProdutorCadastroDto.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        parameters = [
            Parameter(
                `in` = ParameterIn.QUERY, name = "status",
                content = [Content(schema = Schema(type = "string", allowableValues = ["pending", "approved", "rejected"]))],
                description = "Filtro opcional"
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
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Página de cadastros",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = PageableDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "status fora de pending, approved ou rejected", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/cadastros")
    @PreAuthorize("hasRole('ADMIN')")
    fun getCadastros(
        @RequestParam(required = false) status: String?,
        @Parameter(hidden = true)
        @PageableDefault(size = 10, sort = ["producerCreatedAt"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<PageableDto> {
        val filtro = status?.let { valor ->
            ProducerStatus.entries.find { it.name == valor }
                ?: throw ParametroInvalidoException("status inválido: use pending, approved ou rejected")
        }
        return ResponseEntity.ok(paginaDeCadastros(filtro, pageable))
    }

    @Operation(
        summary = "Aprovar, rejeitar ou reabrir um cadastro",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Transições: pending -> approved, pending -> rejected (reason obrigatório) e rejected -> pending (reabrir). " +
            "Outras -> 409. Aprovado já consegue fazer login e aparece no catálogo.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Cadastro atualizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutorCadastroDto::class))]
            ),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Produtor inexistente", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "409", description = "Transição de status não permitida", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "422", description = "status inválido ou reason ausente ao rejeitar", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    fun patchStatus(@PathVariable id: Int, @Valid @RequestBody dto: ProdutorStatusDto): ResponseEntity<ProdutorCadastroDto> {
        val novo = ProducerStatus.valueOf(dto.status!!)
        // Depende de outro campo (status), por isso não é uma anotação no DTO
        if (novo == ProducerStatus.rejected && dto.reason.isNullOrBlank()) {
            throw CampoInvalidoException("reason", "informe o motivo da rejeição")
        }
        val producer = producerService.mudarStatus(id, novo, dto.reason)
        return ResponseEntity.ok(ProdutorMapper.toCadastroDto(producer, producerService.buscarDocumento(id)))
    }

    @Operation(
        summary = "Baixar o documento enviado no cadastro",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN. " +
            "Devolve o arquivo como foi enviado (qualquer formato), com Content-Type e Content-Disposition (nome original).",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "200", description = "Conteúdo do arquivo"),
            ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]),
            ApiResponse(responseCode = "404", description = "Produtor inexistente ou sem documento", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))])
        ]
    )
    @GetMapping("/{id}/documento")
    @PreAuthorize("hasRole('ADMIN')")
    fun getDocumento(@PathVariable id: Int): ResponseEntity<ByteArray> {
        val (documento, conteudo) = producerService.baixarDocumento(id)
        val tipo = runCatching { MediaType.parseMediaType(documento.contentType) }.getOrDefault(MediaType.APPLICATION_OCTET_STREAM)
        return ResponseEntity.ok()
            .contentType(tipo)
            .contentLength(conteudo.size.toLong())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(documento.originalName, StandardCharsets.UTF_8).build().toString()
            )
            .body(conteudo)
    }
}
