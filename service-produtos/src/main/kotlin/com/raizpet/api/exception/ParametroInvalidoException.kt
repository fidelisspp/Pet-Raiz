package com.raizpet.api.exception

/** Parâmetro de consulta com valor inválido (ex.: petType=peixe, ids=abc): vira 400. */
class ParametroInvalidoException(message: String) : RuntimeException(message)
