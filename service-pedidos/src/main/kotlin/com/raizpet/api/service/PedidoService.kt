package com.raizpet.api.service

import com.raizpet.api.entity.PaymentStatus
import com.raizpet.api.entity.PurchaseOrder
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.PurchaseOrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Consulta e pagamento de pedidos. Pedido de outro cliente responde 404 (como se não existisse).
 * Os métodos devolvem o pedido com tudo carregado (endereço, pagamento, grupos e itens), porque
 * open-in-view está desligado e o mapper roda fora da transação.
 */
@Service
class PedidoService(private val purchaseOrderRepository: PurchaseOrderRepository) {

    @Transactional(readOnly = true)
    fun buscarDoCliente(id: Int, customerId: Int): PurchaseOrder = carregar(buscar(id, customerId))

    @Transactional(readOnly = true)
    fun historico(customerId: Int, pageable: Pageable): Page<PurchaseOrder> =
        purchaseOrderRepository.findAllByCustomerId(customerId, pageable).map(::carregar)

    /** Simula a confirmação do pagamento (ainda sem gateway): pending -> paid + confirmed_at. */
    @Transactional
    fun confirmarPagamento(id: Int, customerId: Int): PurchaseOrder {
        val order = buscar(id, customerId)
        val payment = order.payment!!
        if (payment.status != PaymentStatus.pending) {
            throw ConflitoException("Pagamento do pedido id=$id não está pendente (status atual: ${payment.status})")
        }
        payment.status = PaymentStatus.paid
        payment.confirmedAt = LocalDateTime.now()
        return carregar(order)
    }

    private fun buscar(id: Int, customerId: Int): PurchaseOrder =
        purchaseOrderRepository.findByIdAndCustomerId(id, customerId)
            ?: throw EntityNotFoundException("Pedido id=$id não encontrado")

    // Inicializa as associações LAZY dentro da transação (default_batch_fetch_size agrupa as consultas)
    private fun carregar(order: PurchaseOrder): PurchaseOrder {
        order.address.street
        order.payment?.status
        order.groups.forEach { it.items.size }
        return order
    }
}
