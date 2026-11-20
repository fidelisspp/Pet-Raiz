package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.jwt.JwtUsuario
import com.raizpet.api.service.EnderecoService
import com.raizpet.api.web.dto.EnderecoDto
import com.raizpet.api.web.dto.EnderecoResponseDto
import com.raizpet.api.web.dto.mapper.EnderecoMapper
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

@Tag(name = "Endereços", description = "Endereços de entrega do cliente logado (perfil CUSTOMER)")
@RestController
@RequestMapping("/api/v1/enderecos")
@PreAuthorize("hasRole('CUSTOMER')")
class EnderecoController(private val enderecoService: EnderecoService) {

    @Operation(
        summary = "Listar meus endereços",
        description = "Requisição exige um Bearer Token de CUSTOMER. Só os endereços do id do token, por id.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Lista de endereços (pode ser vazia)",
                content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = EnderecoResponseDto::class)))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Perfil diferente de CUSTOMER",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    fun listar(@AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<List<EnderecoResponseDto>> =
        ResponseEntity.ok(enderecoService.listar(usuario.id).map(EnderecoMapper::toDto))

    @Operation(
        summary = "Buscar um endereço meu",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Endereço localizado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = EnderecoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Endereço inexistente ou de outro cliente",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<EnderecoResponseDto> =
        ResponseEntity.ok(EnderecoMapper.toDto(enderecoService.buscar(id, usuario.id)))

    @Operation(
        summary = "Cadastrar endereço",
        description = "O dono é o id do token. recipientName = quem recebe a entrega. state = sigla da UF; zipCode = 00000-000.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "201", description = "Endereço criado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = EnderecoResponseDto::class))]
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
                responseCode = "422", description = "Dados de entrada inválidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping
    fun criar(
        @Valid @RequestBody dto: EnderecoDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<EnderecoResponseDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(EnderecoMapper.toDto(enderecoService.criar(dto, usuario.id)))

    @Operation(
        summary = "Alterar endereço",
        description = "Substitui todos os campos. Pedidos já feitos com este endereço passam a mostrar os dados novos.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Endereço alterado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = EnderecoResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Endereço inexistente ou de outro cliente",
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
        @Valid @RequestBody dto: EnderecoDto,
        @AuthenticationPrincipal usuario: JwtUsuario
    ): ResponseEntity<EnderecoResponseDto> =
        ResponseEntity.ok(EnderecoMapper.toDto(enderecoService.atualizar(id, dto, usuario.id)))

    @Operation(
        summary = "Excluir endereço",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Endereço excluído"),
            ApiResponse(
                responseCode = "404", description = "Endereço inexistente ou de outro cliente",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "Endereço já usado em pedido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @DeleteMapping("/{id}")
    fun excluir(@PathVariable id: Int, @AuthenticationPrincipal usuario: JwtUsuario): ResponseEntity<Void> {
        enderecoService.excluir(id, usuario.id)
        return ResponseEntity.noContent().build()
    }
}
