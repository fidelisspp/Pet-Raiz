package com.raizpet.api.web.dto

import java.time.LocalDateTime

// Nenhum DTO de resposta expõe passwordHash

data class UsuarioResponseDto(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String?,
    val role: String,
    val createdAt: LocalDateTime
)

data class ClienteResponseDto(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String?,
    val cpf: String,
    val suspended: Boolean
)

/**
 * Cadastro de produtor na visão do ADMIN (GET /produtores/pendentes, /produtores/cadastros e PATCH /status).
 * document = dados do arquivo enviado no cadastro (null se não enviou); o conteúdo sai em GET /produtores/{id}/documento.
 */
data class ProdutorCadastroDto(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String?,
    val cnpj: String,
    val companyName: String,
    val technicalResponsibleName: String,
    val shortDescription: String,
    val status: String,
    val createdAt: LocalDateTime,
    val reviewedAt: LocalDateTime?,
    val rejectionReason: String?,
    val document: DocumentoResumoDto?
)

data class DocumentoResumoDto(
    val name: String,
    val contentType: String,
    val size: Long,
    val submittedAt: LocalDateTime
)

data class ProdutorResponseDto(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String?,
    val cnpj: String,
    val companyName: String,
    val technicalResponsibleName: String,
    val shortDescription: String,
    val status: String
)

/**
 * Página pública do produtor (GET /api/v1/produtores e /produtores/{id}).
 * Só dados do negócio: nada de e-mail, telefone, responsável técnico ou documentos.
 * imageUrl é o caminho relativo ("/images/x.jpg"); o app junta com a URL base do service-produtos.
 * since = data em que virou produtor (o "Desde" da tela de detalhe).
 */
data class ProdutorPublicoDto(
    val id: Int,
    val companyName: String,
    val shortDescription: String,
    val cnpj: String,
    val imageUrl: String?,
    val city: String?,
    val state: String?,
    val since: LocalDateTime,
    val verified: Boolean
)

/**
 * Perfil do produtor logado (GET /api/v1/produtores/me), para a área do produtor no app.
 * id = id do usuário (o mesmo do token e do producer_id dos produtos).
 * status: pending, approved ou rejected (na prática approved, porque só aprovado consegue logar).
 */
data class ProdutorMeDto(
    val id: Int,
    val companyName: String,
    val shortDescription: String,
    val imageUrl: String?,
    val city: String?,
    val state: String?,
    val status: String
)

/**
 * Item da lista interna GET /api/v1/interno/produtores/aprovados, consumida pelo service-produtos.
 * Tem o mínimo que o catálogo precisa: o id (para filtrar producer_id IN ...) e o que aparece no card.
 */
data class ProdutorAprovadoDto(
    val id: Int,
    val companyName: String,
    val cnpj: String
)

/**
 * Resposta de GET /api/v1/interno/clientes/{id}, consumida pelo service-pedidos no checkout.
 * Só o necessário para decidir se o cliente pode comprar.
 */
data class ClienteInternoDto(
    val id: Int,
    val suspended: Boolean
)

/** Rota interna GET /interno/clientes (Etapa 5.5): o nome para a tela Pedidos do admin no service-pedidos. */
data class ClienteNomeInternoDto(
    val id: Int,
    val name: String
)
