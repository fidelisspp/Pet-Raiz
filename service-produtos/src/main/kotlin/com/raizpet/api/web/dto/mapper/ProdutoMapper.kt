package com.raizpet.api.web.dto.mapper

import com.raizpet.api.client.ProdutorAprovado
import com.raizpet.api.entity.PetType
import com.raizpet.api.entity.Product
import com.raizpet.api.web.dto.IMAGEM_SEM_FOTO
import com.raizpet.api.web.dto.ProdutoCreateDto
import com.raizpet.api.web.dto.ProdutoCriadoResponseDto
import com.raizpet.api.web.dto.ProdutoInternoDto
import com.raizpet.api.web.dto.ProdutoResponseDto
import com.raizpet.api.web.dto.ProdutorResumoDto

object ProdutoMapper {

    // producerId e category são resolvidos pelo ProductService (token e categoryId)
    fun toProduct(dto: ProdutoCreateDto) = Product().apply {
        name = dto.name!!.trim()
        description = dto.description?.takeIf { it.isNotBlank() }
        price = dto.price!!
        stock = dto.stock!!
        imageUrl = dto.imageUrl ?: IMAGEM_SEM_FOTO
        petType = PetType.deTexto(dto.petType) ?: PetType.both
    }

    /**
     * PUT /produtos/meus/{id}: copia o corpo para o produto que já existe.
     * imageUrl e petType ausentes mantêm o valor atual (o app pode editar só preço/estoque sem reenviar a foto).
     * category é resolvida pelo ProductService.
     */
    fun atualizar(product: Product, dto: ProdutoCreateDto) = product.apply {
        name = dto.name!!.trim()
        description = dto.description?.takeIf { it.isNotBlank() }
        price = dto.price!!
        stock = dto.stock!!
        dto.imageUrl?.let { imageUrl = it }
        PetType.deTexto(dto.petType)?.let { petType = it }
    }

    /** [produtor] vem da rota interna do service-usuarios (só aprovados). */
    fun toDto(product: Product, produtor: ProdutorAprovado) = ProdutoResponseDto(
        id = requireNotNull(product.id),
        name = product.name,
        description = product.description,
        price = product.price,
        stock = product.stock,
        imageUrl = product.imageUrl,
        petType = product.petType.name,
        category = CategoriaMapper.toDto(product.category),
        producer = ProdutorResumoDto(
            id = produtor.id,
            companyName = produtor.companyName,
            cnpj = produtor.cnpj,
            verified = true
        )
    )

    fun toCriadoDto(product: Product) = ProdutoCriadoResponseDto(
        id = requireNotNull(product.id),
        name = product.name,
        description = product.description,
        price = product.price,
        stock = product.stock,
        imageUrl = product.imageUrl,
        petType = product.petType.name,
        active = product.active,
        category = CategoriaMapper.toDto(product.category),
        producerId = product.producerId
    )

    fun toInternoDto(product: Product) = ProdutoInternoDto(
        id = requireNotNull(product.id),
        name = product.name,
        price = product.price,
        stock = product.stock,
        imageUrl = product.imageUrl,
        active = product.active,
        producerId = product.producerId
    )
}
