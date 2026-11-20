package com.raizpet.api.entity

import jakarta.persistence.*
import java.io.Serializable

/** Um carrinho por cliente (uk_cart_customer), criado no primeiro acesso. */
@Entity
@Table(name = "cart")
class Cart : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    // Id do cliente no service-usuarios (sem FK)
    @Column(name = "customer_id", nullable = false, unique = true)
    var customerId: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cart) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

/**
 * Item do carrinho: só productId e quantidade. Nome, preço, estoque e imagem NÃO ficam aqui:
 * são lidos do service-produtos a cada GET /carrinho (podem mudar até o checkout).
 */
@Entity
@Table(name = "cart_item")
class CartItem : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    // FK interna (fk_cart_item_cart), mapeada como coluna simples: o item é sempre consultado pelo carrinho
    @Column(name = "cart_id", nullable = false)
    var cartId: Int = 0

    // Id do produto no service-produtos (sem FK)
    @Column(name = "product_id", nullable = false)
    var productId: Int = 0

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CartItem) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
