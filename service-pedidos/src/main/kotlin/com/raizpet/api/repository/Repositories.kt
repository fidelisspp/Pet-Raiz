package com.raizpet.api.repository

import com.raizpet.api.entity.Address
import com.raizpet.api.entity.Cart
import com.raizpet.api.entity.CartItem
import com.raizpet.api.entity.PurchaseOrder
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

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
}
