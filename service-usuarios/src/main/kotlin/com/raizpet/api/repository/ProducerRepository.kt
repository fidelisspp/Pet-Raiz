package com.raizpet.api.repository

import com.raizpet.api.entity.Producer
import com.raizpet.api.entity.ProducerStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

interface ProducerRepository : JpaRepository<Producer, Int> {

    fun existsByCnpj(cnpj: String): Boolean

    // Rotas públicas (app): paginado
    fun findAllByStatus(status: ProducerStatus, pageable: Pageable): Page<Producer>

    // Lista interna (service-produtos): todos de uma vez, sem paginação
    fun findAllByStatus(status: ProducerStatus, sort: Sort): List<Producer>

    fun findByIdAndStatus(id: Int, status: ProducerStatus): Producer?
}
