package com.raizpet.api.service

import com.raizpet.api.entity.Category
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.CategoryRepository
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryService(private val categoryRepository: CategoryRepository) {

    @Transactional(readOnly = true)
    fun buscarTodas(): List<Category> = categoryRepository.findAll(Sort.by("name"))

    @Transactional(readOnly = true)
    fun buscarPorId(id: Int): Category =
        categoryRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Categoria id=$id não encontrada")
}
