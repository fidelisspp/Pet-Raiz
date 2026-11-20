package com.raizpet.api.web.dto.mapper

import com.raizpet.api.entity.Category
import com.raizpet.api.web.dto.CategoriaResponseDto

object CategoriaMapper {

    fun toDto(category: Category) = CategoriaResponseDto(
        id = requireNotNull(category.id),
        name = category.name
    )

    fun toListDto(categories: List<Category>): List<CategoriaResponseDto> = categories.map(::toDto)
}
