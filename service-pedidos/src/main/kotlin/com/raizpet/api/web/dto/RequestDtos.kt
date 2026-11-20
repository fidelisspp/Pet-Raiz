package com.raizpet.api.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

// Máximo de unidades de um produto por item (mesmo teto da reserva no service-produtos)
const val MAX_QUANTIDADE = 10_000

/** POST e PUT /enderecos. Sem customerId: o dono é sempre o id do token. */
data class EnderecoDto(
    @field:Size(max = 50)
    val label: String? = null,

    @field:NotBlank
    @field:Size(max = 100)
    val recipientName: String? = null,

    @field:NotBlank
    @field:Size(max = 200)
    val street: String? = null,

    @field:NotBlank
    @field:Size(max = 10)
    val number: String? = null,

    @field:Size(max = 100)
    val complement: String? = null,

    @field:NotBlank
    @field:Size(max = 100)
    val neighborhood: String? = null,

    @field:NotBlank
    @field:Size(max = 100)
    val city: String? = null,

    @field:NotBlank
    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "deve ser a sigla da UF, ex.: SP")
    val state: String? = null,

    @field:NotBlank
    @field:Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "deve ser um CEP no formato 00000-000")
    val zipCode: String? = null
)

/** POST /carrinho/itens */
data class CarrinhoItemCreateDto(
    @field:NotNull
    val productId: Int? = null,

    @field:NotNull
    @field:Positive
    @field:Max(MAX_QUANTIDADE.toLong())
    val quantity: Int? = null
)

/** PATCH /carrinho/itens/{productId}: a nova quantidade total do item (não soma). */
data class CarrinhoItemUpdateDto(
    @field:NotNull
    @field:Positive
    @field:Max(MAX_QUANTIDADE.toLong())
    val quantity: Int? = null
)

/** POST /pedidos (checkout do carrinho inteiro). */
data class PedidoCreateDto(
    @field:NotNull
    val addressId: Int? = null,

    @field:NotNull
    @field:Pattern(regexp = "^(standard|express)$", message = "deve ser standard ou express")
    val shippingMethod: String? = null,

    @field:NotNull
    @field:Pattern(regexp = "^(pix|card)$", message = "deve ser pix ou card")
    val paymentMethod: String? = null
)
