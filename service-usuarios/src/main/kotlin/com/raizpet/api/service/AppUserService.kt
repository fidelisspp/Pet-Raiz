package com.raizpet.api.service

import com.raizpet.api.entity.AppUser
import com.raizpet.api.entity.Customer
import com.raizpet.api.entity.Producer
import com.raizpet.api.entity.ProducerStatus
import com.raizpet.api.exception.AccountBlockedException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.exception.PasswordInvalidException
import com.raizpet.api.repository.AppUserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppUserService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional(readOnly = true)
    fun buscarPorId(id: Int): AppUser =
        appUserRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Usuário id=$id não encontrado")

    @Transactional(readOnly = true)
    fun buscarPorEmail(email: String): AppUser =
        appUserRepository.findByEmail(email)
            ?: throw EntityNotFoundException("Usuário com '$email' não encontrado")

    @Transactional(readOnly = true)
    fun buscarTodos(): List<AppUser> = appUserRepository.findAll()

    @Transactional
    fun editarSenha(id: Int, senhaAtual: String, novaSenha: String, confirmaSenha: String): AppUser {
        if (novaSenha != confirmaSenha) {
            throw PasswordInvalidException("Nova senha não confere com confirmação de senha.")
        }

        val user = buscarPorId(id)
        if (!passwordEncoder.matches(senhaAtual, user.passwordHash)) {
            throw PasswordInvalidException("Sua senha não confere")
        }

        user.passwordHash = encode(novaSenha)
        return user
    }

    // Regras de bloqueio do login, aplicadas depois que a senha já foi conferida
    fun verificarAcessoPermitido(user: AppUser) {
        when (user) {
            is Producer -> when (user.status) {
                ProducerStatus.pending ->
                    throw AccountBlockedException("Seu cadastro está em análise, aguarde aprovação")
                // O motivo vem da análise do admin (Etapa 5.1): é a única forma de o produtor saber o que corrigir
                ProducerStatus.rejected -> throw AccountBlockedException(
                    user.rejectionReason?.let { "Cadastro de produtor rejeitado. Motivo: $it" } ?: "Cadastro de produtor rejeitado"
                )
                ProducerStatus.approved -> Unit
            }
            is Customer -> if (user.suspended) throw AccountBlockedException("Conta suspensa")
        }
    }

    fun encode(rawPassword: String): String =
        requireNotNull(passwordEncoder.encode(rawPassword)) { "PasswordEncoder retornou hash nulo" }
}
