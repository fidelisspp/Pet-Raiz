package com.raizpet.api.repository

import com.raizpet.api.entity.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Int> {

    fun findByEmail(email: String): AppUser?

    fun existsByEmail(email: String): Boolean
}
