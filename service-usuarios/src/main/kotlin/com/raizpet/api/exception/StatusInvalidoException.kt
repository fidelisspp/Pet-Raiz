package com.raizpet.api.exception

/** Mudança de status que a regra não permite (ex.: aprovar quem já foi aprovado): vira 409. */
class StatusInvalidoException(message: String) : RuntimeException(message)
