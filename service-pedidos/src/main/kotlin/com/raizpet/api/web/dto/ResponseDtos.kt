package com.raizpet.api.web.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class EnderecoResponseDto(
    val id: Int,
    val label: String?,
    val recipientName: String,
    val street: String,
    val number: String,
    val complement: String?,
    val neighborhood: String,
    val city: String,
    val state: String,
    val zipCode: String
)

/**
 * Item do carrinho. Nome, preço, estoque, imagem e producerId vêm do service-produtos na hora do GET.
 * available = false quando o produto foi desativado ou não existe mais (aí os dados do produto vêm
 * null, se ele não existir); o item fica no carrinho para o cliente remover, mas não entra no subtotal
 * e o checkout é recusado (409) pelo service-produtos.
 * imageUrl é o caminho relativo ("/images/x.jpg"): o app junta com a URL base do service-produtos.
 */
data class CarrinhoItemResponseDto(
    val productId: Int,
    val name: String?,
    val price: BigDecimal?,
    val stock: Int?,
    val imageUrl: String?,
    val producerId: Int?,
    val quantity: Int,
    val subtotal: BigDecimal,
    val available: Boolean
)

data class CarrinhoResponseDto(
    val id: Int,
    val items: List<CarrinhoItemResponseDto>,
    // Soma das quantidades (o número do "badge" do carrinho)
    val totalQuantity: Int,
    // Soma de price * quantity dos itens disponíveis. Sem frete: o frete é escolhido no checkout
    val subtotal: BigDecimal
)

data class PedidoItemResponseDto(
    val productId: Int,
    val productName: String,
    val productImageUrl: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

/** Parte do pedido de um produtor, com o status de entrega dela. */
data class PedidoProdutorResponseDto(
    val producerId: Int,
    val deliveryStatus: String,
    val items: List<PedidoItemResponseDto>,
    val subtotal: BigDecimal
)

data class PagamentoResponseDto(
    // "pix" ou "card" (no banco: pix | credit_card)
    val method: String,
    val status: String,
    val amount: BigDecimal,
    val createdAt: LocalDateTime,
    val confirmedAt: LocalDateTime?
)

/**
 * Pedido completo: itens agrupados por produtor.
 * itemsAmount = soma dos itens; totalAmount = itemsAmount + shippingAmount (= payment.amount).
 * address é o endereço atual (a tabela address é editável; o pedido guarda só o address_id).
 */
data class PedidoResponseDto(
    val id: Int,
    val createdAt: LocalDateTime,
    val address: EnderecoResponseDto,
    val shippingMethod: String,
    val shippingAmount: BigDecimal,
    val itemsAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val payment: PagamentoResponseDto,
    val producers: List<PedidoProdutorResponseDto>
)

/**
 * Pedido recebido pelo PRODUTOR: a parte dele (order_producer_group) num pedido pago.
 * id = id do grupo (é o {grupoId} das rotas); orderId = id do pedido do cliente.
 * total = soma só dos itens dele (o frete é do pedido inteiro e não entra aqui).
 * nextStatus = status que o PATCH aceita agora (null quando já está delivered).
 * address é o endereço atual da tabela address (o pedido guarda só o address_id).
 */
data class PedidoRecebidoResponseDto(
    val id: Int,
    val orderId: Int,
    val createdAt: LocalDateTime,
    val paidAt: LocalDateTime?,
    val status: String,
    val nextStatus: String?,
    val shippingMethod: String,
    val recipientName: String,
    val address: EnderecoResponseDto,
    val items: List<PedidoItemResponseDto>,
    val total: BigDecimal
)
