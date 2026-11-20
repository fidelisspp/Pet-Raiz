package com.raizpet.api.web.controller

import com.raizpet.api.config.SpringDocOpenApiConfig.Companion.SECURITY_SCHEME
import com.raizpet.api.jwt.JwtUserDetails
import com.raizpet.api.service.AppUserService
import com.raizpet.api.service.CustomerService
import com.raizpet.api.service.ProducerService
import com.raizpet.api.web.dto.*
import com.raizpet.api.web.dto.mapper.ClienteMapper
import com.raizpet.api.web.dto.mapper.ProdutorMapper
import com.raizpet.api.web.dto.mapper.UsuarioMapper
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

@Tag(name = "Usuários", description = "Cadastro de clientes e produtores, consulta de usuários e troca de senha")
@RestController
@RequestMapping("/api/v1/usuarios")
class UsuarioController(
    private val appUserService: AppUserService,
    private val customerService: CustomerService,
    private val producerService: ProducerService
) {

    @Operation(
        summary = "Cadastrar cliente", description = "Recurso público. Cria o usuário e o perfil de cliente na mesma transação.",
        responses = [
            ApiResponse(
                responseCode = "201", description = "Cliente criado com sucesso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ClienteResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "E-mail ou CPF já cadastrado no sistema",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Recurso não processado por dados de entrada invalidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/clientes")
    fun createCliente(@Valid @RequestBody dto: ClienteCreateDto): ResponseEntity<ClienteResponseDto> {
        val customer = customerService.salvar(ClienteMapper.toCustomer(dto))
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteMapper.toDto(customer))
    }

    @Operation(
        summary = "Cadastrar produtor",
        description = "Recurso público. Cria o usuário e o perfil de produtor com status 'pending': o login só é liberado após aprovação.",
        responses = [
            ApiResponse(
                responseCode = "201", description = "Produtor criado com sucesso (em análise)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ProdutorResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "409", description = "E-mail ou CNPJ já cadastrado no sistema",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Recurso não processado por dados de entrada invalidos",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PostMapping("/produtores")
    fun createProdutor(@Valid @RequestBody dto: ProdutorCreateDto): ResponseEntity<ProdutorResponseDto> {
        val producer = producerService.salvar(ProdutorMapper.toProducer(dto))
        return ResponseEntity.status(HttpStatus.CREATED).body(ProdutorMapper.toDto(producer))
    }

    @Operation(
        summary = "Dados do usuário logado", description = "Requisição exige um Bearer Token. Qualquer perfil.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Recurso recuperado com sucesso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = UsuarioResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Token ausente ou inválido",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal userDetails: JwtUserDetails): ResponseEntity<UsuarioResponseDto> {
        val user = appUserService.buscarPorId(userDetails.id)
        return ResponseEntity.ok(UsuarioMapper.toDto(user))
    }

    @Operation(
        summary = "Recuperar um usuário pelo id",
        description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN ou ao próprio usuário.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Recurso recuperado com sucesso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = UsuarioResponseDto::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Usuário sem permissão para acessar esse recurso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Recurso não encontrado",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') OR #id == authentication.principal.id")
    fun getById(@PathVariable id: Int): ResponseEntity<UsuarioResponseDto> {
        val user = appUserService.buscarPorId(id)
        return ResponseEntity.ok(UsuarioMapper.toDto(user))
    }

    @Operation(
        summary = "Atualizar senha", description = "Requisição exige um Bearer Token. Só o próprio usuário altera a sua senha.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(responseCode = "204", description = "Senha atualizada com sucesso", content = [Content()]),
            ApiResponse(
                responseCode = "400", description = "Senha atual não confere ou confirmação diferente da nova senha",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Usuário sem permissão para acessar esse recurso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            ),
            ApiResponse(
                responseCode = "422", description = "Campos invalidos ou mal formatados",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @PatchMapping("/{id}")
    @PreAuthorize("#id == authentication.principal.id")
    fun updatePassword(@PathVariable id: Int, @Valid @RequestBody dto: UsuarioSenhaDto): ResponseEntity<Void> {
        appUserService.editarSenha(id, dto.currentPassword!!, dto.newPassword!!, dto.confirmPassword!!)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "Listar todos os usuários", description = "Requisição exige um Bearer Token. Acesso restrito a ADMIN.",
        security = [SecurityRequirement(name = SECURITY_SCHEME)],
        responses = [
            ApiResponse(
                responseCode = "200", description = "Lista com todos os usuários cadastrados",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = UsuarioResponseDto::class))
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Usuário sem permissão para acessar esse recurso",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorMessage::class))]
            )
        ]
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getAll(): ResponseEntity<List<UsuarioResponseDto>> =
        ResponseEntity.ok(UsuarioMapper.toListDto(appUserService.buscarTodos()))
}
