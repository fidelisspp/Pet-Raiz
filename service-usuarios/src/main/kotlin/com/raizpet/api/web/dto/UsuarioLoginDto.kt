package com.raizpet.api.web.dto

import jakarta.validation.constraints.Email
import tools.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Campos nullable com default: campo ausente no JSON vira 422 (Bean Validation), não 400 do Jackson
data class UsuarioLoginDto(
    @field:NotBlank
    @param:JsonDeserialize(using = EmailDeserializer::class)
    @field:Email(message = "O formato de e-mail está invalido", regexp = EMAIL_REGEX)
    val email: String? = null,

    @field:NotBlank
    @field:Size(min = 6, max = 100)
    val password: String? = null
)
