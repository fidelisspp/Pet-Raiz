package com.raizpet.api.entity

import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Pedido: um por checkout. Os itens ficam agrupados por produtor (OrderProducerGroup), cada grupo
 * com o seu status de entrega. total_amount = soma dos itens + frete (shipping_amount).
 */
@Entity
@Table(name = "purchase_order")
class PurchaseOrder : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    // Id do cliente no service-usuarios (sem FK)
    @Column(name = "customer_id", nullable = false)
    var customerId: Int = 0

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false)
    lateinit var address: Address

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method", nullable = false)
    var shippingMethod: ShippingMethod = ShippingMethod.standard

    @Column(name = "shipping_amount", nullable = false, precision = 10, scale = 2)
    var shippingAmount: BigDecimal = BigDecimal.ZERO

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    // Etapa 5.5: preenchido quando o admin cancela (todas as partes ficam canceled)
    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null

    @OneToMany(mappedBy = "purchaseOrder", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id")
    var groups: MutableList<OrderProducerGroup> = mutableListOf()

    @OneToOne(mappedBy = "purchaseOrder", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var payment: Payment? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PurchaseOrder) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

/** Parte do pedido de UM produtor (uk_order_producer_group: purchase_order_id + producer_id). */
@Entity
@Table(name = "order_producer_group")
class OrderProducerGroup : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    lateinit var purchaseOrder: PurchaseOrder

    // Id do produtor no service-usuarios (sem FK); vem do producerId do service-produtos
    @Column(name = "producer_id", nullable = false)
    var producerId: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    var deliveryStatus: DeliveryStatus = DeliveryStatus.pending

    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id")
    var items: MutableList<OrderItem> = mutableListOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderProducerGroup) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

/**
 * Item comprado. unit_price, product_name e product_image_url são CÓPIAS do momento da compra
 * (decisão G): o histórico não muda se o produto for editado ou removido no service-produtos.
 */
@Entity
@Table(name = "order_item")
class OrderItem : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_producer_group_id", nullable = false)
    lateinit var group: OrderProducerGroup

    // Id do produto no service-produtos (sem FK)
    @Column(name = "product_id", nullable = false)
    var productId: Int = 0

    @Column(name = "product_name", nullable = false, length = 150)
    var productName: String = ""

    @Column(name = "product_image_url", nullable = false, length = 500)
    var productImageUrl: String = ""

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderItem) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

/** Pagamento do pedido (uk_payment_purchase_order: um por pedido). Nasce pending no checkout. */
@Entity
@Table(name = "payment")
class Payment : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
    lateinit var purchaseOrder: PurchaseOrder

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: PaymentMethod = PaymentMethod.pix

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = PaymentStatus.pending

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO

    // Integração com Stripe fica para depois: por enquanto sempre null
    @Column(name = "stripe_session_id", length = 255)
    var stripeSessionId: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Payment) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
