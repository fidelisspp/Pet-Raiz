package com.raizpet.api.service

import com.raizpet.api.entity.Producer
import com.raizpet.api.entity.ProducerStatus
import com.raizpet.api.exception.CnpjUniqueViolationException
import com.raizpet.api.exception.EmailUniqueViolationException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.AppUserRepository
import com.raizpet.api.repository.ProducerRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProducerService(
    private val producerRepository: ProducerRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService
) {

    @Transactional
    fun salvar(producer: Producer): Producer {
        if (appUserRepository.existsByEmail(producer.email)) {
            throw EmailUniqueViolationException("E-mail '${producer.email}' já cadastrado")
        }
        if (producerRepository.existsByCnpj(producer.cnpj)) {
            throw CnpjUniqueViolationException("CNPJ '${producer.cnpj}' não pode ser cadastrado, já existe no sistema")
        }
        producer.status = ProducerStatus.pending
        producer.passwordHash = appUserService.encode(producer.passwordHash)
        return producerRepository.save(producer)
    }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Int): Producer =
        producerRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Produtor id=$id não encontrado no sistema")

    // ---- Visão pública: produtor pendente ou rejeitado é tratado como inexistente ----

    @Transactional(readOnly = true)
    fun buscarAprovados(pageable: Pageable): Page<Producer> =
        producerRepository.findAllByStatus(ProducerStatus.approved, pageable)

    @Transactional(readOnly = true)
    fun buscarAprovadoPorId(id: Int): Producer =
        producerRepository.findByIdAndStatus(id, ProducerStatus.approved)
            ?: throw EntityNotFoundException("Produtor id=$id não encontrado")

    /** Todos os aprovados de uma vez: o service-produtos usa para filtrar o catálogo (uma chamada por página). */
    @Transactional(readOnly = true)
    fun buscarTodosAprovados(): List<Producer> =
        producerRepository.findAllByStatus(ProducerStatus.approved, Sort.by("id"))
}
