package com.raizpet.api.exception

/** Dependência (service-usuarios) fora do ar ou respondendo errado: vira 503 no ApiExceptionHandler. */
class ServicoIndisponivelException(
    message: String = "Catálogo temporariamente indisponível: o serviço de usuários não respondeu. Tente novamente em instantes."
) : RuntimeException(message)
