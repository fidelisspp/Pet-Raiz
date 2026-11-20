package com.raizpet.api.web.dto.mapper

import com.raizpet.api.entity.AppUser
import com.raizpet.api.web.dto.UsuarioResponseDto

object UsuarioMapper {

    fun toDto(user: AppUser) = UsuarioResponseDto(
        id = requireNotNull(user.id),
        name = user.name,
        email = user.email,
        phone = user.phone,
        role = user.userType.role,
        createdAt = user.createdAt
    )

    fun toListDto(users: List<AppUser>): List<UsuarioResponseDto> = users.map(::toDto)
}
