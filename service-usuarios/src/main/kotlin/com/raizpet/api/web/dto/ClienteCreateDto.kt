package com.raizpet.api.web.dto

import jakarta.validation.constraints.Email
import tools.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.br.CPF

data class ClienteCreateDto(
    @field:NotBlank
    @field:Size(min = 5, max = 100)
    val name: String? = null,

    @field:NotBlank
    @param:JsonDeserialize(using = EmailDeserializer::class)
    @field:Email(message = "O formato de e-mail está invalido", regexp = EMAIL_REGEX)
    @field:Size(max = 255)
    val email: String? = null,

    @field:Size(max = 20)
    val phone: String? = null,

    @field:NotBlank
    @field:Size(min = 6, max = 100)
    val password: String? = null,

    // Aceita com ou sem pontuação; é gravado com máscara
    @field:NotBlank
    @field:CPF
    val cpf: String? = null
)
