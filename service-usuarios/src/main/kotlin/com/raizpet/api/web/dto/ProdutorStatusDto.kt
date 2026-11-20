package com.raizpet.api.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Corpo do PATCH /api/v1/produtores/{id}/status (ADMIN).
 * approved | rejected decidem a análise; pending reabre um cadastro rejeitado.
 * reason é obrigatório quando status = rejected (conferido no controller: depende de outro campo).
 */
data class ProdutorStatusDto(
    @field:NotBlank
    @field:Pattern(regexp = "pending|approved|rejected", message = "deve ser pending, approved ou rejected")
    val status: String? = null,

    @field:Size(max = 500)
    val reason: String? = null
)
