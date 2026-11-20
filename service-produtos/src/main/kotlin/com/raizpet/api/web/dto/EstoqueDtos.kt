package com.raizpet.api.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ItemEstoqueDto(
    @field:NotNull
    val productId: Int? = null,

    @field:NotNull
    @field:Positive
    @field:Max(10_000)
    val quantity: Int? = null
)

/**
 * Corpo da reserva E do estorno: a MESMA lista de itens (decisão da compensação simplificada,
 * ver service-pedidos/README.md). Produto repetido na lista tem as quantidades somadas.
 */
data class EstoqueItensDto(
    @field:NotEmpty
    @field:Size(max = 100)
    @field:Valid
    val itens: List<ItemEstoqueDto>? = null
)
