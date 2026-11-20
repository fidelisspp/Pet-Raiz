package com.raizpet.api.jwt

import com.raizpet.api.entity.AppUser
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.userdetails.User

class JwtUserDetails(user: AppUser) : User(
    user.email,
    user.passwordHash,
    AuthorityUtils.createAuthorityList("ROLE_" + user.userType.role)
) {
    // Lidos via SpEL nos @PreAuthorize (ex.: #id == authentication.principal.id)
    val id: Int = requireNotNull(user.id)
    val role: String = user.userType.role
}
