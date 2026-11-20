package com.raizpet.api.web.dto

import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal

// Caminho relativo de imagem servida por este serviço: /images/<arquivo>.<jpg|jpeg|png|webp>
const val IMAGE_PATH_REGEX = "^/images/[A-Za-z0-9._-]+\\.(jpg|jpeg|png|webp)$"

// Imagem gravada quando o produtor cadastra sem foto (arquivo em static/images/). Assim imageUrl nunca é null
// nas respostas, e o app e o service-pedidos não precisam tratar produto sem imagem.
const val IMAGEM_SEM_FOTO = "/images/sem-foto.png"

/**
 * Corpo do POST /produtos e do PUT /produtos/meus/{id}. Não tem producerId: o dono é sempre o id do token.
 * imageUrl e petType são opcionais: no POST, ausentes viram IMAGEM_SEM_FOTO e both;
 * no PUT, ausentes mantêm o valor atual do produto.
 */
data class ProdutoCreateDto(
    @field:NotBlank
    @field:Size(max = 150)
    val name: String? = null,

    val description: String? = null,

    @field:NotNull
    @field:Positive
    @field:Digits(integer = 8, fraction = 2)
    val price: BigDecimal? = null,

    @field:NotNull
    @field:PositiveOrZero
    val stock: Int? = null,

    // Opcional. O banco guarda só o caminho relativo, não a URL completa.
    // Se vier, precisa ser um caminho válido (string vazia também é 422).
    @field:Pattern(regexp = IMAGE_PATH_REGEX, message = "deve ser um caminho no formato /images/arquivo.jpg")
    @field:Size(max = 500)
    val imageUrl: String? = null,

    @field:NotNull
    val categoryId: Int? = null,

    // Opcional (no POST, padrão both, como no banco)
    @field:Pattern(regexp = "^(dog|cat|both)$", message = "deve ser dog, cat ou both")
    val petType: String? = null
)
