package com.raizpet.api.web.dto.mapper

import com.raizpet.api.entity.Customer
import com.raizpet.api.util.DocumentoUtils
import com.raizpet.api.web.dto.ClienteCreateDto
import com.raizpet.api.web.dto.ClienteResponseDto

object ClienteMapper {

    // passwordHash sai daqui com a senha em texto; o CustomerService criptografa antes de salvar
    fun toCustomer(dto: ClienteCreateDto) = Customer().apply {
        name = dto.name!!.trim()
        email = dto.email!!
        phone = dto.phone?.takeIf { it.isNotBlank() }
        passwordHash = dto.password!!
        cpf = DocumentoUtils.formatarCpf(dto.cpf!!)
    }

    fun toDto(customer: Customer) = ClienteResponseDto(
        id = requireNotNull(customer.id),
        name = customer.name,
        email = customer.email,
        phone = customer.phone,
        cpf = customer.cpf,
        suspended = customer.suspended
    )
}
