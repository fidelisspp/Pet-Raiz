package com.raizpet.api.web.dto

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

// Padrão de e-mail: aceita maiúsculas e "_" na parte antes do @ (ex.: Joao_Silva@Gmail.com).
// Na prática o valor já chega aqui em minúsculas, por causa do EmailDeserializer abaixo.
const val EMAIL_REGEX = "^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"

/** trim + minúsculas: a forma única em que o e-mail é validado, salvo e usado no login. */
fun normalizarEmail(email: String): String = email.trim().lowercase()

/**
 * Normaliza o e-mail no momento em que o JSON vira DTO, ou seja, ANTES do Bean Validation (@Email)
 * e antes de chegar no controller. Assim " Joao@Gmail.com " é validado, salvo e autenticado
 * como "joao@gmail.com", tanto no cadastro quanto no login.
 * Uso: @param:JsonDeserialize(using = EmailDeserializer::class) no campo email do DTO.
 */
class EmailDeserializer : ValueDeserializer<String>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? =
        p.valueAsString?.let(::normalizarEmail)
}
