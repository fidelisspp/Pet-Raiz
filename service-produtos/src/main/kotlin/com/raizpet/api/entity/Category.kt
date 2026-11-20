package com.raizpet.api.entity

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "category")
class Category : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "name", nullable = false, unique = true, length = 100)
    var name: String = ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Category) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
