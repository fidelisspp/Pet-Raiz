package com.raizpet.api.repository

import com.raizpet.api.entity.ProducerDocument
import org.springframework.data.jpa.repository.JpaRepository

interface ProducerDocumentRepository : JpaRepository<ProducerDocument, Int> {

    // O cadastro envia um documento; se um dia houver reenvio, vale o mais recente
    fun findFirstByProducerIdOrderByIdDesc(producerId: Int): ProducerDocument?
}
