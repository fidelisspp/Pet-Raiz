package com.raizpet.api.web.dto.mapper

import com.raizpet.api.entity.Producer
import com.raizpet.api.entity.ProducerDocument
import com.raizpet.api.entity.ProducerStatus
import com.raizpet.api.util.DocumentoUtils
import com.raizpet.api.web.dto.DocumentoResumoDto
import com.raizpet.api.web.dto.ProdutorAprovadoDto
import com.raizpet.api.web.dto.ProdutorCadastroDto
import com.raizpet.api.web.dto.ProdutorCreateDto
import com.raizpet.api.web.dto.ProdutorMeDto
import com.raizpet.api.web.dto.ProdutorPublicoDto
import com.raizpet.api.web.dto.ProdutorResponseDto

object ProdutorMapper {

    // passwordHash sai daqui com a senha em texto; o ProducerService criptografa antes de salvar
    fun toProducer(dto: ProdutorCreateDto) = Producer().apply {
        name = dto.name!!.trim()
        email = dto.email!!
        phone = dto.phone?.takeIf { it.isNotBlank() }
        passwordHash = dto.password!!
        cnpj = DocumentoUtils.formatarCnpj(dto.cnpj!!)
        companyName = dto.companyName!!.trim()
        technicalResponsibleName = dto.technicalResponsibleName!!.trim()
        shortDescription = dto.shortDescription!!.trim()
    }

    fun toDto(producer: Producer) = ProdutorResponseDto(
        id = requireNotNull(producer.id),
        name = producer.name,
        email = producer.email,
        phone = producer.phone,
        cnpj = producer.cnpj,
        companyName = producer.companyName,
        technicalResponsibleName = producer.technicalResponsibleName,
        shortDescription = producer.shortDescription,
        status = producer.status.name
    )

    // Visão pública (app): o ProducerService só entrega produtores aprovados para cá
    fun toPublicoDto(producer: Producer) = ProdutorPublicoDto(
        id = requireNotNull(producer.id),
        companyName = producer.companyName,
        shortDescription = producer.shortDescription,
        cnpj = producer.cnpj,
        imageUrl = producer.imageUrl,
        city = producer.city,
        state = producer.state,
        since = producer.producerCreatedAt,
        verified = producer.status == ProducerStatus.approved
    )

    // Visão do próprio produtor (GET /produtores/me)
    fun toMeDto(producer: Producer) = ProdutorMeDto(
        id = requireNotNull(producer.id),
        companyName = producer.companyName,
        shortDescription = producer.shortDescription,
        imageUrl = producer.imageUrl,
        city = producer.city,
        state = producer.state,
        status = producer.status.name
    )

    // Visão do admin (Etapa 5.1): tudo o que o produtor enviou e o resultado da análise
    fun toCadastroDto(producer: Producer, documento: ProducerDocument?) = ProdutorCadastroDto(
        id = requireNotNull(producer.id),
        name = producer.name,
        email = producer.email,
        phone = producer.phone,
        cnpj = producer.cnpj,
        companyName = producer.companyName,
        technicalResponsibleName = producer.technicalResponsibleName,
        shortDescription = producer.shortDescription,
        status = producer.status.name,
        createdAt = producer.producerCreatedAt,
        reviewedAt = producer.reviewedAt,
        rejectionReason = producer.rejectionReason,
        document = documento?.let {
            DocumentoResumoDto(
                name = it.originalName,
                contentType = it.contentType,
                size = it.sizeBytes,
                submittedAt = it.submittedAt
            )
        }
    )

    // Visão interna (service-produtos): só o necessário para o catálogo
    fun toAprovadoDto(producer: Producer) = ProdutorAprovadoDto(
        id = requireNotNull(producer.id),
        companyName = producer.companyName,
        cnpj = producer.cnpj
    )
}
