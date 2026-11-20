package com.raizpet.api.exception

/** Parâmetro de consulta fora dos valores aceitos (ex.: ?status=xyz): vira 400. */
class ParametroInvalidoException(message: String) : RuntimeException(message)
