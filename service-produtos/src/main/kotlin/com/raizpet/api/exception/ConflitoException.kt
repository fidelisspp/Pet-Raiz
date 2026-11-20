package com.raizpet.api.exception

/** Regra de negócio que impede a operação (ex.: nome de categoria repetido, categoria com produtos): vira 409. */
class ConflitoException(message: String) : RuntimeException(message)
