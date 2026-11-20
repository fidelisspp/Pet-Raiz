package com.raizpet.api.service

import com.raizpet.api.entity.DeliveryStatus
import com.raizpet.api.entity.OrderProducerGroup
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.OrderProducerGroupRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Pedidos recebidos pelo PRODUTOR: cada "pedido recebido" é a parte dele num pedido
 * (order_producer_group), só de pedidos pagos. Grupo de outro produtor ou de pedido não pago = 404.
 *
 * Não chama nenhum outro serviço: o producer_id veio do service-produtos no checkout, e o nome do
 * destinatário e o endereço estão aqui mesmo (tabela address).
 */
@Service
class PedidoRecebidoService(private val groupRepository: OrderProducerGroupRepository) {

    /** A ordem (mais recentes primeiro) é fixa na consulta; o sort que vier do cliente é ignorado. */
    @Transactional(readOnly = true)
    fun listar(producerId: Int, status: DeliveryStatus?, pageable: Pageable): Page<OrderProducerGroup> =
        groupRepository
            .findRecebidos(producerId, status, PageRequest.of(pageable.pageNumber, pageable.pageSize))
            .map(::carregar)

    @Transactional(readOnly = true)
    fun buscar(id: Int, producerId: Int): OrderProducerGroup =
        carregar(groupRepository.findRecebido(id, producerId) ?: naoEncontrado(id))

    /**
     * Só avança UMA etapa: pending -> preparing -> shipped -> delivered.
     * Voltar, pular ou repetir o status atual = 409 (ConflitoException).
     */
    @Transactional
    fun avancarStatus(id: Int, producerId: Int, novo: DeliveryStatus): OrderProducerGroup {
        val group = groupRepository.findRecebidoForUpdate(id, producerId) ?: naoEncontrado(id)
        val atual = group.deliveryStatus
        val permitido = proximo(atual)
        if (novo != permitido) {
            val motivo = if (permitido == null) "o pedido já foi entregue" else "o próximo status permitido é $permitido"
            throw ConflitoException("Não é possível mudar o status de $atual para $novo: $motivo")
        }
        group.deliveryStatus = novo
        return carregar(group)
    }

    private fun naoEncontrado(id: Int): Nothing = throw EntityNotFoundException("Pedido recebido id=$id não encontrado")

    // Inicializa as associações LAZY dentro da transação (open-in-view está desligado)
    private fun carregar(group: OrderProducerGroup): OrderProducerGroup {
        group.purchaseOrder.address.street
        group.purchaseOrder.payment?.confirmedAt
        group.items.size
        return group
    }

    companion object {
        /** Próximo status da sequência, ou null se já está no último (delivered). */
        fun proximo(status: DeliveryStatus): DeliveryStatus? = DeliveryStatus.entries.getOrNull(status.ordinal + 1)
    }
}
