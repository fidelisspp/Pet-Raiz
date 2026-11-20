package com.raizpet.api.service

import com.raizpet.api.entity.Producer
import com.raizpet.api.entity.ProducerDocument
import com.raizpet.api.entity.ProducerStatus
import com.raizpet.api.exception.CnpjUniqueViolationException
import com.raizpet.api.exception.EmailUniqueViolationException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.exception.StatusInvalidoException
import com.raizpet.api.repository.AppUserRepository
import com.raizpet.api.repository.ProducerDocumentRepository
import com.raizpet.api.repository.ProducerRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@Service
class ProducerService(
    private val producerRepository: ProducerRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val producerDocumentRepository: ProducerDocumentRepository,
    private val documentoService: DocumentoService
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

    /**
     * Cadastro com o documento de regularidade (multipart). O documento é validado antes de tocar no banco;
     * se gravar o arquivo falhar, a transação desfaz o produtor criado.
     */
    @Transactional
    fun salvarComDocumento(producer: Producer, documento: MultipartFile?): Producer {
        documentoService.validar(documento)
        val arquivo = documento!!
        val salvo = salvar(producer)
        producerDocumentRepository.save(
            ProducerDocument(
                producerId = requireNotNull(salvo.id),
                documentUrl = documentoService.salvar(arquivo),
                originalName = arquivo.originalFilename?.takeIf { it.isNotBlank() }?.take(255) ?: "documento",
                contentType = arquivo.contentType?.takeIf { it.isNotBlank() }?.take(150) ?: "application/octet-stream",
                sizeBytes = arquivo.size
            )
        )
        return salvo
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

    // ---- Visão do admin (Etapa 5.1) ----

    /** Cadastros de um status, ou de todos com [status] null. A ordem vem do Pageable do controller. */
    @Transactional(readOnly = true)
    fun buscarCadastros(status: ProducerStatus?, pageable: Pageable): Page<Producer> =
        if (status == null) producerRepository.findAll(pageable) else producerRepository.findAllByStatus(status, pageable)

    @Transactional(readOnly = true)
    fun buscarDocumento(producerId: Int): ProducerDocument? =
        producerDocumentRepository.findFirstByProducerIdOrderByIdDesc(producerId)

    /** Registro do documento e o conteúdo do arquivo; 404 se o produtor não existe ou não enviou documento. */
    @Transactional(readOnly = true)
    fun baixarDocumento(producerId: Int): Pair<ProducerDocument, ByteArray> {
        buscarPorId(producerId)
        val documento = buscarDocumento(producerId)
            ?: throw EntityNotFoundException("Produtor id=$producerId não enviou documento")
        return documento to documentoService.ler(documento.documentUrl)
    }

    /**
     * Decisão do admin. Transições permitidas:
     * pending -> approved, pending -> rejected (com motivo) e rejected -> pending (reabrir a análise).
     * Qualquer outra (aprovar de novo, rejeitar um aprovado...) -> 409.
     */
    @Transactional
    fun mudarStatus(id: Int, novo: ProducerStatus, motivo: String?): Producer {
        val producer = buscarPorId(id)
        val atual = producer.status
        val permitido = when (atual) {
            ProducerStatus.pending -> novo == ProducerStatus.approved || novo == ProducerStatus.rejected
            ProducerStatus.rejected -> novo == ProducerStatus.pending
            ProducerStatus.approved -> false
        }
        if (!permitido) {
            val regra = when (atual) {
                ProducerStatus.pending -> "um cadastro em análise só pode ser aprovado ou rejeitado"
                ProducerStatus.rejected -> "um cadastro rejeitado só pode voltar para análise (pending)"
                ProducerStatus.approved -> "um cadastro aprovado não muda mais de status"
            }
            throw StatusInvalidoException("Não é possível mudar o cadastro de ${atual.name} para ${novo.name}: $regra")
        }
        producer.status = novo
        when (novo) {
            ProducerStatus.approved -> {
                producer.rejectionReason = null
                producer.reviewedAt = LocalDateTime.now()
            }
            ProducerStatus.rejected -> {
                producer.rejectionReason = requireNotNull(motivo).trim()
                producer.reviewedAt = LocalDateTime.now()
            }
            ProducerStatus.pending -> {
                producer.rejectionReason = null
                producer.reviewedAt = null
            }
        }
        return producer
    }
}
