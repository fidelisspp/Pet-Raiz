package com.raizpet.api.service

import com.raizpet.api.entity.Customer
import com.raizpet.api.exception.CpfUniqueViolationException
import com.raizpet.api.exception.EmailUniqueViolationException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.AppUserRepository
import com.raizpet.api.repository.CustomerRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService
) {

    @Transactional
    fun salvar(customer: Customer): Customer {
        if (appUserRepository.existsByEmail(customer.email)) {
            throw EmailUniqueViolationException("E-mail '${customer.email}' já cadastrado")
        }
        if (customerRepository.existsByCpf(customer.cpf)) {
            throw CpfUniqueViolationException("CPF '${customer.cpf}' não pode ser cadastrado, já existe no sistema")
        }
        customer.passwordHash = appUserService.encode(customer.passwordHash)
        return customerRepository.save(customer)
    }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Int): Customer =
        customerRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Cliente id=$id não encontrado no sistema")

    @Transactional(readOnly = true)
    fun buscarTodos(pageable: Pageable): Page<Customer> = customerRepository.findAll(pageable)
}
