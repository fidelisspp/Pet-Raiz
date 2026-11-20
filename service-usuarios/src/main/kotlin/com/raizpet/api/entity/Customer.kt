package com.raizpet.api.entity

import jakarta.persistence.*

@Entity
@Table(name = "customer")
@PrimaryKeyJoinColumn(name = "user_id")
@DiscriminatorValue("customer")
class Customer : AppUser() {

    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    var cpf: String = ""

    @Column(name = "suspended", nullable = false)
    var suspended: Boolean = false

    override val userType: UserType get() = UserType.customer
}
