package com.raizpet.api.service

import com.raizpet.api.client.ItemEstoque
import com.raizpet.api.client.ProdutosClient
import com.raizpet.api.client.UsuariosClient
import com.raizpet.api.entity.DeliveryStatus
import com.raizpet.api.entity.OrderProducerGroup
import com.raizpet.api.entity.PaymentStatus
import com.raizpet.api.entity.PurchaseOrder
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.exception.ServicoIndisponivelException
import com.raizpet.api.repository.OrderProducerGroupRepository
import com.raizpet.api.repository.PurchaseOrderRepository
import com.raizpet.api.web.dto.ClienteComprasDto
import com.raizpet.api.web.dto.ParteAdminDto
import com.raizpet.api.web.dto.PedidoAdminDto
import com.raizpet.api.web.dto.PedidosResumoDto
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

/**
 * Visão do ADMIN sobre os pedidos. Etapa 5.3: compras por cliente. Etapa 5.5: lista com busca e filtro,
 * resumo, cancelamento (com estorno do estoque) e correção do status de uma parte.
 */
@Service
class PedidoAdminService(
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val groupRepository: OrderProducerGroupRepository,
    private val usuariosClient: UsuariosClient,
    private val produtosClient: ProdutosClient,
    private val transactionTemplate: TransactionTemplate
) {

    companion object {
        const val MAX_IDS = 100

        /** Pago há mais que isso e com parte ainda não enviada = atrasado (decisão da Etapa 5.5). */
        val PRAZO_ENVIO: Duration = Duration.ofDays(3)

        val FILTROS = setOf("pendentes", "enviados", "entregues", "atrasados", "cancelados")
        private val NAO_ENVIADOS = setOf(DeliveryStatus.pending, DeliveryStatus.preparing)
        private val log = LoggerFactory.getLogger(PedidoAdminService::class.java)
    }

    /** Um item por id pedido, na mesma ordem; quem não tem pedido pago vem com 0 e 0.00. */
    @Transactional(readOnly = true)
    fun comprasPorCliente(ids: List<Int>): List<ClienteComprasDto> {
        val unicos = ids.distinct()
        if (unicos.isEmpty()) throw ParametroInvalidoException("Informe ao menos um id em ids")
        if (unicos.size > MAX_IDS) throw ParametroInvalidoException("No máximo $MAX_IDS ids por consulta")
        val resumo = purchaseOrderRepository.resumirPagosPorCliente(unicos).associate { linha ->
            (linha[0] as Number).toInt() to ((linha[1] as Number).toLong() to (linha[2] as BigDecimal))
        }
        return unicos.map { id ->
            val (pedidos, total) = resumo[id] ?: (0L to BigDecimal.ZERO)
            ClienteComprasDto(id, pedidos, total.setScale(2))
        }
    }

    // ================= Etapa 5.5 =================

    /**
     * Lista do admin. A busca pelo nome do cliente pergunta ao service-usuarios quais ids casam; os nomes da
     * página também vêm de lá, numa segunda chamada. As duas ficam FORA da transação do banco e, se falharem,
     * a lista sai sem os nomes (a busca por nome é que não funciona nesse caso).
     */
    fun listar(busca: String?, filtro: String?, pageable: Pageable): Page<PedidoAdminDto> {
        val situacao = filtro?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (situacao != null && situacao !in FILTROS) {
            throw ParametroInvalidoException("status inválido: use ${FILTROS.joinToString(", ")}")
        }
        val termo = busca?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() }
        val numero = termo?.toIntOrNull()
        val clientesDaBusca = termo?.takeIf { numero == null }?.let { t ->
            try {
                usuariosClient.buscarNomes(busca = t).map { it.id }
            } catch (ex: ServicoIndisponivelException) {
                emptyList()
            }
        }.orEmpty().ifEmpty { listOf(-1) }

        val agora = LocalDateTime.now()
        val pagina = transactionTemplate.execute {
            purchaseOrderRepository
                .buscarParaAdmin(termo == null, numero, clientesDaBusca, situacao, agora.minus(PRAZO_ENVIO), pageable)
                .map { toDto(it, agora, nome = null) }
        }!!

        val nomes = try {
            pagina.content.map { it.customerId }.distinct().takeIf { it.isNotEmpty() }
                ?.let { ids -> usuariosClient.buscarNomes(ids = ids).associate { it.id to it.name } }
                .orEmpty()
        } catch (ex: ServicoIndisponivelException) {
            emptyMap()
        }
        return pagina.map { it.copy(customerName = nomes[it.customerId]) }
    }

    @Transactional(readOnly = true)
    fun resumo(): PedidosResumoDto {
        val agora = LocalDateTime.now()
        return PedidosResumoDto(
            last30Days = purchaseOrderRepository.countByCreatedAtGreaterThanEqual(agora.minusDays(30)),
            late = purchaseOrderRepository.contarParaAdmin("atrasados", agora.minus(PRAZO_ENVIO))
        )
    }

    /**
     * Cancela o pedido inteiro. Só se nenhuma parte foi enviada (todas pending ou preparing).
     * Todas as partes viram canceled, o estoque reservado no checkout volta (estorno no service-produtos)
     * e, se o pedido estava pago, o pagamento vira refunded (pagamento simulado: não há dinheiro a devolver).
     * O estorno roda dentro da transação, com a linha do pedido travada: se ele falhar (503), nada muda aqui.
     */
    fun cancelar(id: Int): PedidoAdminDto {
        val dto = transactionTemplate.execute {
            val order = purchaseOrderRepository.findByIdForUpdate(id)
                ?: throw EntityNotFoundException("Pedido id=$id não encontrado")
            if (order.canceledAt != null) throw ConflitoException("O pedido #$id já foi cancelado")
            val enviados = order.groups.filter { it.deliveryStatus !in NAO_ENVIADOS }
            if (enviados.isNotEmpty()) {
                throw ConflitoException(
                    "O pedido #$id não pode ser cancelado: ${enviados.size} produtor(es) já enviaram a parte deles. " +
                        "Só dá para cancelar enquanto nenhuma parte saiu para entrega."
                )
            }
            val itens = order.groups.flatMap { it.items }
                .groupBy { it.productId }
                .map { (produto, linhas) -> ItemEstoque(produto, linhas.sumOf { it.quantity }) }
            try {
                produtosClient.estornar(itens)
            } catch (ex: Exception) {
                // O estornar deixa a falha subir crua (o checkout trata do jeito dele): aqui vira 503 e nada muda
                // Se foi timeout ou conexão caída, o service-produtos PODE ter devolvido o estoque mesmo assim
                // (mesma ambiguidade da reserva no checkout): conferir manualmente o produto no log
                log.error("Estorno falhou ao cancelar o pedido {} (itens {}; se foi timeout, o estoque PODE ter voltado: conferir)", id, itens, ex)
                throw ServicoIndisponivelException(
                    "Não foi possível devolver o estoque agora (serviço de produtos indisponível). O pedido NÃO foi cancelado; tente de novo em instantes."
                )
            }

            order.groups.forEach { it.deliveryStatus = DeliveryStatus.canceled }
            order.canceledAt = LocalDateTime.now()
            order.payment?.takeIf { it.status == PaymentStatus.paid }?.status = PaymentStatus.refunded
            log.info("Pedido {} cancelado pelo admin; estoque devolvido: {}", id, itens)
            toDto(order, LocalDateTime.now(), nome = null)
        }!!
        return comNome(dto)
    }

    /**
     * Correção manual do status de UMA parte: qualquer um dos 4 (pode voltar), diferente do produtor,
     * que só avança uma etapa. Parte cancelada ou pedido ainda não pago -> 409.
     */
    fun corrigirStatus(groupId: Int, novo: DeliveryStatus): PedidoAdminDto {
        val dto = transactionTemplate.execute {
            val group: OrderProducerGroup = groupRepository.findById(groupId).orElse(null)
                ?: throw EntityNotFoundException("Parte de pedido id=$groupId não encontrada")
            val order = group.purchaseOrder
            if (group.deliveryStatus == DeliveryStatus.canceled) {
                throw ConflitoException("O pedido #${order.id} foi cancelado: o status das partes não muda mais")
            }
            if (order.payment?.status != PaymentStatus.paid) {
                throw ConflitoException("O pedido #${order.id} ainda não foi pago: não há entrega para corrigir")
            }
            group.deliveryStatus = novo
            toDto(order, LocalDateTime.now(), nome = null)
        }!!
        return comNome(dto)
    }

    private fun comNome(dto: PedidoAdminDto): PedidoAdminDto = try {
        dto.copy(customerName = usuariosClient.buscarNomes(ids = listOf(dto.customerId)).firstOrNull()?.name)
    } catch (ex: ServicoIndisponivelException) {
        dto
    }

    /** Monta o DTO dentro da transação (as partes e os itens são LAZY e o open-in-view está desligado). */
    private fun toDto(order: PurchaseOrder, agora: LocalDateTime, nome: String?): PedidoAdminDto {
        val pagamento = order.payment
        val pagoEm = pagamento?.confirmedAt?.takeIf { pagamento.status == PaymentStatus.paid }
        val noPrazo = pagoEm == null || !pagoEm.isBefore(agora.minus(PRAZO_ENVIO))
        val partes = order.groups.sortedBy { it.id }.map { g ->
            ParteAdminDto(
                groupId = requireNotNull(g.id),
                producerId = g.producerId,
                deliveryStatus = g.deliveryStatus.name,
                itemCount = g.items.sumOf { it.quantity },
                late = order.canceledAt == null && !noPrazo && g.deliveryStatus in NAO_ENVIADOS
            )
        }
        return PedidoAdminDto(
            id = requireNotNull(order.id),
            createdAt = order.createdAt,
            customerId = order.customerId,
            customerName = nome,
            totalAmount = order.totalAmount,
            paymentStatus = pagamento?.status?.name ?: PaymentStatus.pending.name,
            canceledAt = order.canceledAt,
            cancelable = order.canceledAt == null && order.groups.all { it.deliveryStatus in NAO_ENVIADOS },
            late = partes.any { it.late },
            daysLate = if (partes.any { it.late } && pagoEm != null) Duration.between(pagoEm, agora).toDays().toInt() else 0,
            producers = partes
        )
    }
}
