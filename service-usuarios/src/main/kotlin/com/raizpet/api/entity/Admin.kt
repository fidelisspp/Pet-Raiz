package com.raizpet.api.entity

import jakarta.persistence.*

@Entity
@Table(name = "admin")
@PrimaryKeyJoinColumn(name = "user_id")
@DiscriminatorValue("admin")
class Admin : AppUser() {

    override val userType: UserType get() = UserType.admin
}
