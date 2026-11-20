package com.raizpet.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Documento de regularidade enviado no cadastro do produtor (tabela producer_document, que já existia no schema).
 * documentUrl guarda só o nome do arquivo salvo em <petraiz.uploads.dir>/documentos/ (UUID, sem extensão do cliente).
 * O arquivo não é público: só o admin baixa, por GET /api/v1/produtores/{id}/documento.
 */
@Entity
@Table(name = "producer_document")
class ProducerDocument(
    @Column(name = "producer_id", nullable = false)
    var producerId: Int = 0,

    @Column(name = "document_url", nullable = false, length = 500)
    var documentUrl: String = "",

    @Column(name = "original_name", nullable = false, length = 255)
    var originalName: String = "documento",

    @Column(name = "content_type", nullable = false, length = 150)
    var contentType: String = "application/octet-stream",

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null

    @Column(name = "submitted_at", nullable = false, updatable = false)
    var submittedAt: LocalDateTime = LocalDateTime.now()
}
