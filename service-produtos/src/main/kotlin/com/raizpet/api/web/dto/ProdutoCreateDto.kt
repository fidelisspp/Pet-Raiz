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

// Não tem producerId: o produtor dono é sempre o id do token
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

    // Mudou em relação ao pet-raiz: o banco guarda só o caminho relativo, então não é mais URL completa.
    // Upload de imagem continua fora desta entrega: o arquivo precisa ser colocado em static/images/.
    @field:NotBlank
    @field:Pattern(regexp = IMAGE_PATH_REGEX, message = "deve ser um caminho no formato /images/arquivo.jpg")
    @field:Size(max = 500)
    val imageUrl: String? = null,

    @field:NotNull
    val categoryId: Int? = null,

    // Opcional (padrão both, como no banco)
    @field:Pattern(regexp = "^(dog|cat|both)$", message = "deve ser dog, cat ou both")
    val petType: String? = null
)
