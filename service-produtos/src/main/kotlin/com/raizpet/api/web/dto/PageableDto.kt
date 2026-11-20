package com.raizpet.api.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

// Mesmo formato de página do curso
data class PageableDto(
    val content: List<Any?> = emptyList(),
    val first: Boolean = false,
    val last: Boolean = false,
    @JsonProperty("page")
    val number: Int = 0,
    val size: Int = 0,
    @JsonProperty("pageElements")
    val numberOfElements: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Int = 0
)
