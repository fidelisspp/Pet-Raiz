package com.raizpet.api.exception

/** Upload recusado (ausente, vazio, grande demais ou tipo não aceito): vira 422 com errors.arquivo. */
class ArquivoInvalidoException(message: String) : RuntimeException(message)
