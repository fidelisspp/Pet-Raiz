package com.raizpet.api.repository

import com.raizpet.api.entity.Customer
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Int> {

    fun existsByCpf(cpf: String): Boolean

    /**
     * Lista do admin (Etapa 5.3). [busca] procura no nome e no e-mail (sem diferenciar maiúscula);
     * [suspenso] filtra por status. Os dois são opcionais (null = sem filtro).
     */
    @Query(
        """
        SELECT c FROM Customer c
        WHERE (:busca IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :busca, '%'))
                              OR LOWER(c.email) LIKE LOWER(CONCAT('%', :busca, '%')))
          AND (:suspenso IS NULL OR c.suspended = :suspenso)
        """
    )
    fun buscarParaAdmin(busca: String?, suspenso: Boolean?, pageable: Pageable): Page<Customer>

    // Rota interna da Etapa 5.5 (nomes para a lista de pedidos do admin)
    fun findAllByIdIn(ids: Collection<Int>): List<Customer>

    fun findTop100ByNameContainingIgnoreCaseOrderByName(nome: String): List<Customer>
}
