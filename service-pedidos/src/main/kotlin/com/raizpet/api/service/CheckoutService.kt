package com.raizpet.api.service

import com.raizpet.api.client.ItemEstoque
import com.raizpet.api.client.ProdutoInterno
import com.raizpet.api.client.ProdutosClient
import com.raizpet.api.client.UsuariosClient
import com.raizpet.api.entity.Address
import com.raizpet.api.entity.CartItem
import com.raizpet.api.entity.OrderItem
import com.raizpet.api.entity.OrderProducerGroup
import com.raizpet.api.entity.Payment
import com.raizpet.api.entity.PaymentMethod
import com.raizpet.api.entity.PaymentStatus
import com.raizpet.api.entity.PurchaseOrder
import com.raizpet.api.entity.ShippingMethod
import com.raizpet.api.exception.CarrinhoVazioException
import com.raizpet.api.exception.ClienteBloqueadoException
import com.raizpet.api.exception.PedidoNaoCriadoException
import com.raizpet.api.exception.ServicoIndisponivelException
import com.raizpet.api.repository.CartItemRepository
import com.raizpet.api.repository.PurchaseOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/** Tudo o que a transação local precisa, já conferido e com os produtos que a reserva devolveu. */
data class NovoPedido(
    val customerId: Int,
    val address: Address,
    val shippingMethod: ShippingMethod,
    val paymentMethod: PaymentMethod,
    val itens: List<CartItem>,
    val produtos: Map<Int, ProdutoInterno>
)

/**
 * Checkout com a COMPENSAÇÃO SIMPLIFICADA do README:
 *
 *  0. Confere localmente endereço (do cliente) e carrinho (não vazio); depois, no service-usuarios,
 *     se o cliente não está suspenso. Nada foi alterado em lugar nenhum até aqui.
 *  1. Reserva: service-produtos baixa o estoque de todos os itens ou de nenhum (409 -> carrinho intacto).
 *  2. Pedido: transação local (PedidoTransacao.criar): pedido, grupos, itens, payment pending, esvazia carrinho.
 *  3. Se o passo 2 falhar: estorno com a MESMA lista de itens.
 *  4. Se o estorno também falhar: só log de erro com os itens, para correção manual.
 *
 * Este método NÃO é @Transactional: as chamadas HTTP ficam fora de transação, e a transação do
 * passo 2 precisa terminar (commit ou rollback) antes de decidir pelo estorno.
 */
@Service
class CheckoutService(
    private val enderecoService: EnderecoService,
    private val carrinhoService: CarrinhoService,
    private val usuariosClient: UsuariosClient,
    private val produtosClient: ProdutosClient,
    private val pedidoTransacao: PedidoTransacao
) {

    private val log = LoggerFactory.getLogger(CheckoutService::class.java)

    fun finalizar(customerId: Int, addressId: Int, shippingMethod: ShippingMethod, paymentMethod: PaymentMethod): Int {
        val address = enderecoService.buscar(addressId, customerId)
        val cart = carrinhoService.buscarOuCriar(customerId)
        val itens = carrinhoService.itens(cart)
        if (itens.isEmpty()) throw CarrinhoVazioException()

        val cliente = usuariosClient.buscarCliente(customerId)
            ?: throw ClienteBloqueadoException("Cliente id=$customerId não encontrado no serviço de usuários")
        if (cliente.suspended) {
            throw ClienteBloqueadoException("Conta suspensa: não é possível finalizar pedidos")
        }

        // 1. Reserva (ConflitoException 409 ou ServicoIndisponivelException 503 sobem direto: nada foi baixado)
        val listaEstoque = itens.map { ItemEstoque(it.productId, it.quantity) }
        val reservados = produtosClient.reservar(listaEstoque).associateBy { it.id }

        // 2. Transação local
        return try {
            val faltando = listaEstoque.map { it.productId }.filter { it !in reservados }
            if (faltando.isNotEmpty()) {
                throw ServicoIndisponivelException("Reserva sem os produtos $faltando na resposta do service-produtos")
            }
            pedidoTransacao.criar(NovoPedido(customerId, address, shippingMethod, paymentMethod, itens, reservados))
        } catch (ex: Exception) {
            log.error("Falha ao gravar o pedido do cliente {} depois da reserva; estornando {}", customerId, listaEstoque, ex)
            // 3. Estorno
            val estornado = estornar(customerId, listaEstoque)
            throw PedidoNaoCriadoException(
                if (estornado) "Não foi possível registrar o pedido. Nenhuma cobrança foi feita e o estoque foi liberado; tente novamente."
                else "Não foi possível registrar o pedido. Nenhuma cobrança foi feita; tente novamente."
            )
        }
    }

    private fun estornar(customerId: Int, itens: List<ItemEstoque>): Boolean =
        try {
            produtosClient.estornar(itens)
            log.info("Estorno feito para o cliente {}: {}", customerId, itens)
            true
        } catch (ex: Exception) {
            // 4. Sem retentativa automática (simplificação aceita): correção manual a partir deste log
            log.error(
                "ESTORNO FALHOU - CORRIGIR MANUALMENTE: devolver ao estoque no service-produtos, cliente={}, itens={}. Motivo: {}",
                customerId, itens, ex.message
            )
            false
        }
}

/**
 * Passo 2 do checkout, numa transação LOCAL. Bean separado para o @Transactional valer
 * (chamada de fora pelo proxy) e para a transação já ter terminado quando o CheckoutService
 * decidir pelo estorno.
 */
@Service
class PedidoTransacao(
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val cartItemRepository: CartItemRepository
) {

    @Transactional
    fun criar(novo: NovoPedido): Int {
        val agora = LocalDateTime.now()
        val order = PurchaseOrder().apply {
            customerId = novo.customerId
            address = novo.address
            shippingMethod = novo.shippingMethod
            shippingAmount = novo.shippingMethod.amount
            createdAt = agora
        }

        // Um grupo por produtor; nome, imagem e preço copiados do que a reserva devolveu
        novo.itens.groupBy { novo.produtos.getValue(it.productId).producerId }.toSortedMap().forEach { (producerId, itens) ->
            val grupo = OrderProducerGroup().apply {
                purchaseOrder = order
                this.producerId = producerId
            }
            itens.forEach { item ->
                val p = novo.produtos.getValue(item.productId)
                grupo.items.add(OrderItem().apply {
                    group = grupo
                    productId = p.id
                    productName = p.name
                    productImageUrl = p.imageUrl
                    quantity = item.quantity
                    unitPrice = p.price.setScale(2, RoundingMode.HALF_UP)
                })
            }
            order.groups.add(grupo)
        }

        val itensAmount = order.groups.flatMap { it.items }
            .fold(BigDecimal.ZERO) { soma, i -> soma + i.unitPrice.multiply(BigDecimal(i.quantity)) }
        order.totalAmount = (itensAmount + order.shippingAmount).setScale(2, RoundingMode.HALF_UP)
        order.payment = Payment().apply {
            purchaseOrder = order
            paymentMethod = novo.paymentMethod
            status = PaymentStatus.pending
            amount = order.totalAmount
            createdAt = agora
        }

        purchaseOrderRepository.save(order)
        // Esvazia o carrinho: só os itens que entraram no pedido (um item adicionado durante o checkout fica)
        cartItemRepository.deleteAllByIdInBatch(novo.itens.map { it.id!! })
        return order.id!!
    }
}
