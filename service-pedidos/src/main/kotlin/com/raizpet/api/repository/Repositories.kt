package com.raizpet.api.repository

import com.raizpet.api.entity.Address
import com.raizpet.api.entity.Cart
import com.raizpet.api.entity.CartItem
import com.raizpet.api.entity.DeliveryStatus
import com.raizpet.api.entity.Favorite
import com.raizpet.api.entity.OrderProducerGroup
import com.raizpet.api.entity.PurchaseOrder
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

/*
 * Todas as consultas de dados do cliente recebem o customerId (claim "id" do token):
 * registro de outro cliente simplesmente "não existe" para quem pergunta.
 */

interface AddressRepository : JpaRepository<Address, Int> {

    fun findAllByCustomerIdOrderByIdAsc(customerId: Int): List<Address>

    fun findByIdAndCustomerId(id: Int, customerId: Int): Address?
}

interface CartRepository : JpaRepository<Cart, Int> {

    fun findByCustomerId(customerId: Int): Cart?
}

interface CartItemRepository : JpaRepository<CartItem, Int> {

    fun findAllByCartIdOrderByIdAsc(cartId: Int): List<CartItem>

    fun findByCartIdAndProductId(cartId: Int, productId: Int): CartItem?
}

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, Int> {

    fun findByIdAndCustomerId(id: Int, customerId: Int): PurchaseOrder?

    fun findAllByCustomerId(customerId: Int, pageable: Pageable): Page<PurchaseOrder>

    fun existsByAddressId(addressId: Int): Boolean

    /**
     * Admin (Etapa 5.3): por cliente, [0] customerId, [1] quantidade de pedidos PAGOS e [2] soma do total deles.
     * Cliente sem pedido pago não aparece (o serviço completa com zero).
     */
    @Query(
        """
        SELECT o.customerId, COUNT(o), SUM(o.totalAmount)
        FROM PurchaseOrder o JOIN o.payment p
        WHERE p.status = com.raizpet.api.entity.PaymentStatus.paid AND o.customerId IN :ids
        GROUP BY o.customerId
        """
    )
    fun resumirPagosPorCliente(ids: Collection<Int>): List<Array<Any>>

    // ---- Admin (Etapa 5.5) ----

    /**
     * Todos os pedidos, mais recentes primeiro.
     * Busca: [semBusca] = sem filtro; senão o número do pedido ([numero], se a busca for numérica) OU o cliente
     * estar em [clientes] (ids cujo nome casou, vindos do service-usuarios; nunca vazia: [-1] quando nenhum).
     * [filtro]: null | pendentes | enviados | entregues | atrasados | cancelados (ver PedidoAdminService).
     * [limiteAtraso] = agora - 3 dias: pago antes disso e com parte ainda não enviada = atrasado.
     */
    @Query(
        value = """
        SELECT o FROM PurchaseOrder o LEFT JOIN o.payment p
        WHERE (:semBusca = true OR o.id = :numero OR o.customerId IN :clientes)
          AND (""" + FILTRO_ADMIN + """)
        ORDER BY o.createdAt DESC, o.id DESC
        """,
        countQuery = """
        SELECT COUNT(o) FROM PurchaseOrder o LEFT JOIN o.payment p
        WHERE (:semBusca = true OR o.id = :numero OR o.customerId IN :clientes)
          AND (""" + FILTRO_ADMIN + """)
        """
    )
    fun buscarParaAdmin(
        semBusca: Boolean,
        numero: Int?,
        clientes: Collection<Int>,
        filtro: String?,
        limiteAtraso: LocalDateTime,
        pageable: Pageable
    ): Page<PurchaseOrder>

    @Query(
        "SELECT COUNT(o) FROM PurchaseOrder o LEFT JOIN o.payment p WHERE (" + FILTRO_ADMIN + ")"
    )
    fun contarParaAdmin(filtro: String?, limiteAtraso: LocalDateTime): Long

    fun countByCreatedAtGreaterThanEqual(desde: LocalDateTime): Long

    /** Cancelamento: trava a linha do pedido (dois cancelamentos ao mesmo tempo ficam em fila). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.id = :id")
    fun findByIdForUpdate(id: Int): PurchaseOrder?

    companion object {
        // Os mesmos filtros na lista, na contagem e no resumo
        const val FILTRO_ADMIN = """
            :filtro IS NULL
            OR (:filtro = 'cancelados' AND o.canceledAt IS NOT NULL)
            OR (:filtro = 'pendentes' AND o.canceledAt IS NULL AND EXISTS (
                  SELECT g FROM OrderProducerGroup g WHERE g.purchaseOrder = o
                  AND g.deliveryStatus IN (com.raizpet.api.entity.DeliveryStatus.pending, com.raizpet.api.entity.DeliveryStatus.preparing)))
            OR (:filtro = 'enviados' AND o.canceledAt IS NULL AND EXISTS (
                  SELECT g FROM OrderProducerGroup g WHERE g.purchaseOrder = o
                  AND g.deliveryStatus = com.raizpet.api.entity.DeliveryStatus.shipped))
            OR (:filtro = 'entregues' AND o.canceledAt IS NULL AND NOT EXISTS (
                  SELECT g FROM OrderProducerGroup g WHERE g.purchaseOrder = o
                  AND g.deliveryStatus <> com.raizpet.api.entity.DeliveryStatus.delivered))
            OR (:filtro = 'atrasados' AND o.canceledAt IS NULL
                  AND p.status = com.raizpet.api.entity.PaymentStatus.paid AND p.confirmedAt < :limiteAtraso
                  AND EXISTS (
                  SELECT g FROM OrderProducerGroup g WHERE g.purchaseOrder = o
                  AND g.deliveryStatus IN (com.raizpet.api.entity.DeliveryStatus.pending, com.raizpet.api.entity.DeliveryStatus.preparing)))
        """
    }
}

/*
 * Visão do PRODUTOR: as partes (order_producer_group) com producer_id = id do token, e só de pedidos
 * PAGOS (payment.status = paid, ou refunded quando o admin cancelou depois de pago: o produtor vê a parte
 * dele como canceled). Pedido ainda não pago não existe para o produtor (lista e 404).
 */
interface OrderProducerGroupRepository : JpaRepository<OrderProducerGroup, Int> {

    // Mais recentes primeiro (data do pedido); a ordem é fixa na consulta
    @Query(
        value = """
        SELECT g FROM OrderProducerGroup g
        JOIN g.purchaseOrder o
        JOIN o.payment p
        WHERE g.producerId = :producerId
          AND p.status IN (com.raizpet.api.entity.PaymentStatus.paid, com.raizpet.api.entity.PaymentStatus.refunded)
          AND (:status IS NULL OR g.deliveryStatus = :status)
        ORDER BY o.createdAt DESC, g.id DESC
        """,
        countQuery = """
        SELECT COUNT(g) FROM OrderProducerGroup g
        JOIN g.purchaseOrder o
        JOIN o.payment p
        WHERE g.producerId = :producerId
          AND p.status IN (com.raizpet.api.entity.PaymentStatus.paid, com.raizpet.api.entity.PaymentStatus.refunded)
          AND (:status IS NULL OR g.deliveryStatus = :status)
        """
    )
    fun findRecebidos(producerId: Int, status: DeliveryStatus?, pageable: Pageable): Page<OrderProducerGroup>

    @Query(
        """
        SELECT g FROM OrderProducerGroup g
        JOIN g.purchaseOrder o
        JOIN o.payment p
        WHERE g.id = :id AND g.producerId = :producerId
          AND p.status IN (com.raizpet.api.entity.PaymentStatus.paid, com.raizpet.api.entity.PaymentStatus.refunded)
        """
    )
    fun findRecebido(id: Int, producerId: Int): OrderProducerGroup?

    /**
     * Mesma busca, com SELECT ... FOR UPDATE na linha do grupo: dois PATCH simultâneos no mesmo grupo
     * ficam em fila, e o segundo já enxerga o status gravado pelo primeiro (não avança duas etapas).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT g FROM OrderProducerGroup g
        JOIN g.purchaseOrder o
        JOIN o.payment p
        WHERE g.id = :id AND g.producerId = :producerId
          AND p.status IN (com.raizpet.api.entity.PaymentStatus.paid, com.raizpet.api.entity.PaymentStatus.refunded)
        """
    )
    fun findRecebidoForUpdate(id: Int, producerId: Int): OrderProducerGroup?
}

interface FavoriteRepository : JpaRepository<Favorite, Int> {

    // Mais recentes primeiro
    fun findAllByCustomerIdOrderByCreatedAtDescIdDesc(customerId: Int): List<Favorite>

    fun findByCustomerIdAndProductId(customerId: Int, productId: Int): Favorite?
}
