package com.raizpet.api.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

/**
 * Usuário base (tabela app_user). Cada perfil é uma subclasse com tabela própria
 * ligada pelo mesmo id (herança JOINED); a coluna user_type diz qual subtipo carregar.
 */
@Entity
@Table(name = "app_user")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
    name = "user_type",
    discriminatorType = DiscriminatorType.STRING,
    columnDefinition = "enum('customer','admin','producer')"
)
abstract class AppUser : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "name", nullable = false, length = 100)
    var name: String = ""

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String = ""

    @Column(name = "phone", length = 20)
    var phone: String? = null

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = ""

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    abstract val userType: UserType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppUser) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
