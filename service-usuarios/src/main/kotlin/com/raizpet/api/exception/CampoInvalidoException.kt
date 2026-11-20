package com.raizpet.api.exception

/**
 * Um campo recusado por regra que a anotação de validação não cobre (ex.: motivo obrigatório só ao rejeitar).
 * Vira 422 no mesmo formato da validação: errors = { campo: motivo }.
 */
open class CampoInvalidoException(
    val campo: String,
    motivo: String,
    val titulo: String = "Campo(s) invalido(s)"
) : RuntimeException(motivo)
