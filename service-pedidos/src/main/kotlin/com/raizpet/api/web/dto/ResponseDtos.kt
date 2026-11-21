package com.raizpet.api.web.dto

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Admin (Etapa 5.3): resumo de compras de um cliente para a tela "Clientes".
 * orders = pedidos pagos; totalSpent = soma do total (itens + frete) desses pedidos.
 */
data class ClienteComprasDto(
    val customerId: Int,
    val orders: Long,
    val totalSpent: BigDecimal
)

/**
 * Pedido na visão do ADMIN (Etapa 5.5).
 * customerName vem do service-usuarios: null se ele estiver fora do ar (a lista aparece mesmo assim).
 * late/daysLate: pago há mais de 3 dias e com alguma parte ainda não enviada (pending ou preparing).
 */
data class PedidoAdminDto(
    val id: Int,
    val createdAt: LocalDateTime,
    val customerId: Int,
    val customerName: String?,
    val totalAmount: BigDecimal,
    val paymentStatus: String,
    val canceledAt: LocalDateTime?,
    val cancelable: Boolean,
    val late: Boolean,
    val daysLate: Int,
    val producers: List<ParteAdminDto>
)

/** A parte de um produtor no pedido: groupId é o id usado no PATCH do status. */
data class ParteAdminDto(
    val groupId: Int,
    val producerId: Int,
    val deliveryStatus: String,
    val itemCount: Int,
    val late: Boolean
)

/** Resumo do topo da tela Pedidos do admin: pedidos dos últimos 30 dias e pedidos atrasados (qualquer data). */
data class PedidosResumoDto(
    val last30Days: Long,
    val late: Long
)

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
 * address vem da tabela address (o pedido guarda só o address_id); endereço usado em pedido não pode mais
 * ser alterado nem excluído, então é sempre o endereço da compra.
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
 * address vem da tabela address; endereço usado em pedido é bloqueado para PUT/DELETE (409), então não muda.
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

/**
 * Item de GET /favoritos (e resposta do POST). Nome, preço, estoque, imagem e producerId vêm do
 * service-produtos na hora. available = false quando o produto foi removido/desativado (aí, se ele
 * não existir mais, os dados do produto vêm null): o item continua na lista para o cliente desfavoritar.
 * imageUrl é o caminho relativo ("/images/x.jpg"): o app junta com a URL base do service-produtos.
 */
data class FavoritoResponseDto(
    val productId: Int,
    val name: String?,
    val price: BigDecimal?,
    val stock: Int?,
    val imageUrl: String?,
    val producerId: Int?,
    val available: Boolean,
    val createdAt: LocalDateTime
)
