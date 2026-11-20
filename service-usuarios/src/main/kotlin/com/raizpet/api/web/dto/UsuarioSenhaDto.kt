package com.raizpet.api.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UsuarioSenhaDto(
    @field:NotBlank
    @field:Size(min = 6, max = 100)
    val currentPassword: String? = null,

    @field:NotBlank
    @field:Size(min = 6, max = 100)
    val newPassword: String? = null,

    @field:NotBlank
    @field:Size(min = 6, max = 100)
    val confirmPassword: String? = null
)
