package com.raizpet.api.jwt

/**
 * O "usuário logado" neste serviço: só o que veio nas claims do token (sub, id, role).
 * É o principal da autenticação; nos controllers: @AuthenticationPrincipal usuario: JwtUsuario.
 */
data class JwtUsuario(
    val id: Int,
    val email: String,
    val role: String
)
