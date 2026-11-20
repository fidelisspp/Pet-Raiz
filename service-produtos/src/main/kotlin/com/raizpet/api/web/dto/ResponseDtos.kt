package com.raizpet.api.web.dto

import java.math.BigDecimal

/** Resumo do topo da tela Produtos do admin (Etapa 5.4): publicados e produtores com algo publicado. */
data class ProdutosResumoDto(
    val products: Long,
    val producers: Long
)

/**
 * Categoria na visão do ADMIN (GET /categorias/resumo, Etapa 5.2).
 * productCount = produtos ativos (os que aparecem no app); removedProductCount = removidos pelo produtor
 * (soft delete), que ainda apontam para a categoria e também impedem a exclusão.
 */
data class CategoriaResumoDto(
    val id: Int,
    val name: String,
    val productCount: Long,
    val removedProductCount: Long
)

data class CategoriaResponseDto(
    val id: Int,
    val name: String
)

/**
 * Dados públicos do produtor no catálogo. Mesmo formato do pet-raiz, mas montado com o que vem
 * da rota interna do service-usuarios (id, companyName, cnpj). verified = sempre true, porque o
 * catálogo só mostra produtor aprovado.
 */
data class ProdutorResumoDto(
    val id: Int,
    val companyName: String,
    val cnpj: String,
    val verified: Boolean
)

/**
 * Item do catálogo (GET /produtos e /produtos/{id}). Igual ao pet-raiz + petType.
 * imageUrl é o caminho relativo ("/images/x.jpg"): o app junta com a URL base deste serviço.
 */
data class ProdutoResponseDto(
    val id: Int,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val stock: Int,
    val imageUrl: String,
    val petType: String,
    val category: CategoriaResponseDto,
    val producer: ProdutorResumoDto
)

/**
 * Resposta do POST /produtos. Traz producerId em vez do objeto producer: o cadastro valida só o token
 * (claims id e role) e não consulta o service-usuarios, então não tem companyName/cnpj para devolver.
 */
data class ProdutoCriadoResponseDto(
    val id: Int,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val stock: Int,
    val imageUrl: String,
    val petType: String,
    val active: Boolean,
    val category: CategoriaResponseDto,
    val producerId: Int
)

/**
 * Produto nas rotas internas (consulta por ids, reserva e estorno), para o service-pedidos.
 * Sem dados do produtor além do id: quem precisa conferir o produtor chama o service-usuarios.
 */
data class ProdutoInternoDto(
    val id: Int,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val imageUrl: String,
    val active: Boolean,
    val producerId: Int
)

/**
 * Resposta do upload (POST /produtos/meus/imagens). url é o caminho relativo que o app manda
 * no imageUrl do POST /produtos ou do PUT /produtos/meus/{id}; para exibir, junta com a URL base deste serviço.
 */
data class ImagemResponseDto(
    val url: String
)
