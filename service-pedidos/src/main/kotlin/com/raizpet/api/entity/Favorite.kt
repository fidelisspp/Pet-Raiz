package com.raizpet.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Produto favoritado pelo cliente (uk_favorite: customer_id + product_id, um por par).
 * Só ids: nome, preço e foto vêm do service-produtos a cada GET /favoritos.
 */
@Entity
@Table(name = "favorite")
class Favorite : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    // Id do cliente no service-usuarios (sem FK)
    @Column(name = "customer_id", nullable = false)
    var customerId: Int = 0

    // Id do produto no service-produtos (sem FK)
    @Column(name = "product_id", nullable = false)
    var productId: Int = 0

    // Truncado em segundos: a coluna é TIMESTAMP sem fração. Sem isso, a resposta do POST (valor em memória,
    // com nanossegundos) seria diferente do GET (valor lido do banco, arredondado pelo MySQL).
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Favorite) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
