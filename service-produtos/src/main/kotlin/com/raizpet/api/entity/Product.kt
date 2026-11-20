package com.raizpet.api.entity

import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "product")
class Product : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    // Id do produtor no service-usuarios. Não é FK nem relacionamento JPA: o produtor
    // mora em outro banco. Vem do token no cadastro e é conferido no catálogo pela rota interna.
    @Column(name = "producer_id", nullable = false)
    var producerId: Int = 0

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    lateinit var category: Category

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_type", nullable = false)
    var petType: PetType = PetType.both

    @Column(name = "name", nullable = false, length = 150)
    var name: String = ""

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO

    @Column(name = "stock", nullable = false)
    var stock: Int = 0

    // Só o caminho relativo ("/images/x.jpg"); o app junta com a URL base deste serviço
    @Column(name = "image_url", nullable = false, length = 500)
    var imageUrl: String = ""

    // Soft delete: produto inativo continua no banco, mas some do catálogo
    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Product) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
