package com.raizpet.api.entity

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "address")
class Address : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    // Id do cliente no service-usuarios (claim "id" do token). Sem FK: o cliente mora em outro banco.
    @Column(name = "customer_id", nullable = false)
    var customerId: Int = 0

    // Apelido opcional: "Casa", "Trabalho"
    @Column(name = "label", length = 50)
    var label: String? = null

    // Quem recebe (decisão G): evita chamar o service-usuarios só para saber o nome do cliente
    @Column(name = "recipient_name", nullable = false, length = 100)
    var recipientName: String = ""

    @Column(name = "street", nullable = false, length = 200)
    var street: String = ""

    @Column(name = "number", nullable = false, length = 10)
    var number: String = ""

    @Column(name = "complement", length = 100)
    var complement: String? = null

    @Column(name = "neighborhood", nullable = false, length = 100)
    var neighborhood: String = ""

    @Column(name = "city", nullable = false, length = 100)
    var city: String = ""

    @Column(name = "state", nullable = false, length = 2, columnDefinition = "CHAR(2)")
    var state: String = ""

    @Column(name = "zip_code", nullable = false, length = 9)
    var zipCode: String = ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Address) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
