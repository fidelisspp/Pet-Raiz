package com.raizpet.api.repository

import com.raizpet.api.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Int> {

    fun existsByCpf(cpf: String): Boolean
}
