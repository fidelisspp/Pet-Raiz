package com.raizpet.api.web.dto.mapper

import com.raizpet.api.entity.Category
import com.raizpet.api.web.dto.CategoriaResponseDto
import com.raizpet.api.web.dto.CategoriaResumoDto

object CategoriaMapper {

    fun toDto(category: Category) = CategoriaResponseDto(
        id = requireNotNull(category.id),
        name = category.name
    )

    fun toListDto(categories: List<Category>): List<CategoriaResponseDto> = categories.map(::toDto)

    /** [contagens]: categoria -> (ativos, removidos), do CategoryService.contagens(). */
    fun toResumoDto(category: Category, contagens: Map<Int, Pair<Long, Long>>): CategoriaResumoDto {
        val (ativos, removidos) = contagens[category.id] ?: (0L to 0L)
        return CategoriaResumoDto(requireNotNull(category.id), category.name, ativos, removidos)
    }
}
