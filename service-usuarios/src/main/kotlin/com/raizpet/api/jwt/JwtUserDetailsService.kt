package com.raizpet.api.jwt

import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.service.AppUserService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class JwtUserDetailsService(
    private val appUserService: AppUserService,
    private val jwtUtils: JwtUtils
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails =
        try {
            JwtUserDetails(appUserService.buscarPorEmail(username))
        } catch (ex: EntityNotFoundException) {
            throw UsernameNotFoundException(ex.message ?: "Usuário não encontrado")
        }

    fun getTokenAuthenticated(username: String): JwtToken {
        val user = appUserService.buscarPorEmail(username)
        appUserService.verificarAcessoPermitido(user)
        return jwtUtils.createToken(username, user.userType.role, requireNotNull(user.id))
    }
}
