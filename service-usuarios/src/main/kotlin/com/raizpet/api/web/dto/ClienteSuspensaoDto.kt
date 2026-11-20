package com.raizpet.api.web.dto

import jakarta.validation.constraints.NotNull

/** Corpo do PATCH /api/v1/clientes/{id}/suspensao (ADMIN): true suspende, false reativa. */
data class ClienteSuspensaoDto(
    @field:NotNull
    val suspended: Boolean? = null
)
