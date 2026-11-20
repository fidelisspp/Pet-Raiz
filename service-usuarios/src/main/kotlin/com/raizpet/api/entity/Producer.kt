package com.raizpet.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "producer")
@PrimaryKeyJoinColumn(name = "user_id")
@DiscriminatorValue("producer")
class Producer : AppUser() {

    @Column(name = "cnpj", nullable = false, unique = true, length = 18)
    var cnpj: String = ""

    @Column(name = "company_name", nullable = false, length = 200)
    var companyName: String = ""

    @Column(name = "technical_responsible_name", nullable = false, length = 150)
    var technicalResponsibleName: String = ""

    @Column(name = "short_description", nullable = false, length = 500)
    var shortDescription: String = ""

    // Todo produtor nasce em análise; o admin aprova ou rejeita (Etapa 5.1, PATCH /produtores/{id}/status)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProducerStatus = ProducerStatus.pending

    // Data do cadastro como produtor (coluna própria de producer, além de app_user.created_at)
    @Column(name = "created_at", nullable = false, updatable = false)
    var producerCreatedAt: LocalDateTime = LocalDateTime.now()

    // Dados públicos opcionais (decisão 5, docs/db/alteracoes-etapa2.sql).
    // imageUrl é só o caminho relativo ("/images/x.jpg"): o arquivo é servido pelo service-produtos.
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null

    @Column(name = "city", length = 100)
    var city: String? = null

    @Column(name = "state", length = 2, columnDefinition = "CHAR(2)")
    var state: String? = null

    // Resultado da análise (docs/db/alteracoes-etapa5-1.sql). O motivo só existe quando status = rejected
    @Column(name = "rejection_reason", length = 500)
    var rejectionReason: String? = null

    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null

    override val userType: UserType get() = UserType.producer
}
