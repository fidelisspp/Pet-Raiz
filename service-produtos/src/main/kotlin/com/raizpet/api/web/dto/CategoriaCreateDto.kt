package com.raizpet.api.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Corpo do POST /categorias e do PUT /categorias/{id} (ADMIN). O nome é gravado sem espaços nas pontas. */
data class CategoriaCreateDto(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String? = null
)
