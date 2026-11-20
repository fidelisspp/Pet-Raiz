package com.raizpet.api.repository

import com.raizpet.api.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Int> {

    // O MySQL compara pelo collation da coluna (sem diferenciar maiúscula nem acento, como o UNIQUE)
    fun existsByNameIgnoreCase(name: String): Boolean

    fun existsByNameIgnoreCaseAndIdNot(name: String, id: Int): Boolean
}
