package com.raizpet.api.web.dto.mapper

import com.raizpet.api.entity.Address
import com.raizpet.api.entity.OrderItem
import com.raizpet.api.entity.OrderProducerGroup
import com.raizpet.api.entity.PurchaseOrder
import com.raizpet.api.service.CarrinhoComProdutos
import com.raizpet.api.service.FavoritoComProduto
import com.raizpet.api.service.PedidoRecebidoService
import com.raizpet.api.web.dto.CarrinhoItemResponseDto
import com.raizpet.api.web.dto.CarrinhoResponseDto
import com.raizpet.api.web.dto.EnderecoDto
import com.raizpet.api.web.dto.EnderecoResponseDto
import com.raizpet.api.web.dto.FavoritoResponseDto
import com.raizpet.api.web.dto.PagamentoResponseDto
import com.raizpet.api.web.dto.PedidoItemResponseDto
import com.raizpet.api.web.dto.PedidoProdutorResponseDto
import com.raizpet.api.web.dto.PedidoRecebidoResponseDto
import com.raizpet.api.web.dto.PedidoResponseDto
import java.math.BigDecimal
import java.math.RoundingMode

object FavoritoMapper {

    fun toDto(f: FavoritoComProduto): FavoritoResponseDto {
        val p = f.produto
        return FavoritoResponseDto(
            productId = f.favorite.productId,
            name = p?.name,
            price = p?.price,
            stock = p?.stock,
            imageUrl = p?.imageUrl,
            producerId = p?.producerId,
            available = p != null && p.active,
            createdAt = f.favorite.createdAt
        )
    }
}

object EnderecoMapper {

    /** Copia os campos do DTO (POST ou PUT) para a entidade; o customerId é responsabilidade do service. */
    fun copiar(dto: EnderecoDto, address: Address = Address()): Address = address.apply {
        label = dto.label?.trim()?.takeIf { it.isNotEmpty() }
        recipientName = dto.recipientName!!.trim()
        street = dto.street!!.trim()
        number = dto.number!!.trim()
        complement = dto.complement?.trim()?.takeIf { it.isNotEmpty() }
        neighborhood = dto.neighborhood!!.trim()
        city = dto.city!!.trim()
        state = dto.state!!.trim().uppercase()
        zipCode = dto.zipCode!!.trim().let { if (it.length == 8) "${it.take(5)}-${it.drop(5)}" else it }
    }

    fun toDto(address: Address) = EnderecoResponseDto(
        id = requireNotNull(address.id),
        label = address.label,
        recipientName = address.recipientName,
        street = address.street,
        number = address.number,
        complement = address.complement,
        neighborhood = address.neighborhood,
        city = address.city,
        state = address.state,
        zipCode = address.zipCode
    )
}

object CarrinhoMapper {

    fun toDto(carrinho: CarrinhoComProdutos): CarrinhoResponseDto {
        val itens = carrinho.itens.map { item ->
            val p = carrinho.produtos[item.productId]
            val disponivel = p != null && p.active
            CarrinhoItemResponseDto(
                productId = item.productId,
                name = p?.name,
                price = p?.price,
                stock = p?.stock,
                imageUrl = p?.imageUrl,
                producerId = p?.producerId,
                quantity = item.quantity,
                subtotal = if (disponivel) p!!.price.multiply(BigDecimal(item.quantity)).setScale(2, RoundingMode.HALF_UP) else BigDecimal.ZERO.setScale(2),
                available = disponivel
            )
        }
        return CarrinhoResponseDto(
            id = requireNotNull(carrinho.cart.id),
            items = itens,
            totalQuantity = itens.sumOf { it.quantity },
            subtotal = itens.fold(BigDecimal.ZERO.setScale(2)) { soma, i -> soma + i.subtotal }
        )
    }
}

object PedidoMapper {

    private fun subtotal(item: OrderItem): BigDecimal = item.unitPrice.multiply(BigDecimal(item.quantity)).setScale(2, RoundingMode.HALF_UP)

    private fun itens(g: OrderProducerGroup): List<PedidoItemResponseDto> = g.items.sortedBy { it.id }.map {
        PedidoItemResponseDto(
            productId = it.productId,
            productName = it.productName,
            productImageUrl = it.productImageUrl,
            quantity = it.quantity,
            unitPrice = it.unitPrice,
            subtotal = subtotal(it)
        )
    }

    /** Visão do produtor: a parte dele no pedido. Precisa do grupo carregado (PedidoRecebidoService devolve assim). */
    fun toRecebidoDto(g: OrderProducerGroup): PedidoRecebidoResponseDto {
        val order = g.purchaseOrder
        val itens = itens(g)
        return PedidoRecebidoResponseDto(
            id = requireNotNull(g.id),
            orderId = requireNotNull(order.id),
            createdAt = order.createdAt,
            paidAt = order.payment?.confirmedAt,
            status = g.deliveryStatus.name,
            nextStatus = PedidoRecebidoService.proximo(g.deliveryStatus)?.name,
            shippingMethod = order.shippingMethod.name,
            recipientName = order.address.recipientName,
            address = EnderecoMapper.toDto(order.address),
            items = itens,
            total = itens.fold(BigDecimal.ZERO.setScale(2)) { soma, i -> soma + i.subtotal }
        )
    }

    /** Precisa ser chamado com o pedido inteiro carregado (PedidoService devolve assim). */
    fun toDto(order: PurchaseOrder): PedidoResponseDto {
        val payment = requireNotNull(order.payment) { "Pedido ${order.id} sem pagamento" }
        val grupos = order.groups.sortedBy { it.producerId }.map { g ->
            val itens = itens(g)
            PedidoProdutorResponseDto(
                producerId = g.producerId,
                deliveryStatus = g.deliveryStatus.name,
                items = itens,
                subtotal = itens.fold(BigDecimal.ZERO.setScale(2)) { soma, i -> soma + i.subtotal }
            )
        }
        return PedidoResponseDto(
            id = requireNotNull(order.id),
            createdAt = order.createdAt,
            address = EnderecoMapper.toDto(order.address),
            shippingMethod = order.shippingMethod.name,
            shippingAmount = order.shippingAmount,
            itemsAmount = grupos.fold(BigDecimal.ZERO.setScale(2)) { soma, g -> soma + g.subtotal },
            totalAmount = order.totalAmount,
            payment = PagamentoResponseDto(
                method = payment.paymentMethod.api,
                status = payment.status.name,
                amount = payment.amount,
                createdAt = payment.createdAt,
                confirmedAt = payment.confirmedAt
            ),
            producers = grupos
        )
    }
}
