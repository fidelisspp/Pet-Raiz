package com.raizpet.api.web.dto.mapper

import com.raizpet.api.web.dto.PageableDto
import org.springframework.data.domain.Page

object PageableMapper {

    fun toDto(page: Page<*>) = PageableDto(
        content = page.content,
        first = page.isFirst,
        last = page.isLast,
        number = page.number,
        size = page.size,
        numberOfElements = page.numberOfElements,
        totalPages = page.totalPages,
        totalElements = page.totalElements.toInt()
    )
}
