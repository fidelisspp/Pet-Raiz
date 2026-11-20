package com.raizpet.api.service

import com.raizpet.api.client.ProdutoInterno
import com.raizpet.api.client.ProdutosClient
import com.raizpet.api.entity.Cart
import com.raizpet.api.entity.CartItem
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.CartItemRepository
import com.raizpet.api.repository.CartRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** Carrinho com os dados atuais dos produtos (vindos do service-produtos). */
data class CarrinhoComProdutos(
    val cart: Cart,
    val itens: List<CartItem>,
    val produtos: Map<Int, ProdutoInterno>
)

/**
 * Carrinho do cliente. Só guarda productId e quantidade; o resto vem do service-produtos.
 *
 * Os métodos NÃO são @Transactional de propósito: a chamada HTTP ao service-produtos acontece fora
 * de transação (não segura conexão do banco esperando outro serviço). As escritas usam um
 * TransactionTemplate curto, depois da consulta.
 *
 * O estoque é conferido ao adicionar/alterar (409 se passar do disponível), mas NÃO é reservado:
 * quem garante o estoque de verdade é a reserva no checkout.
 */
@Service
class CarrinhoService(
    private val cartRepository: CartRepository,
    private val cartItemRepository: CartItemRepository,
    private val produtosClient: ProdutosClient,
    private val transactionTemplate: TransactionTemplate
) {

    /** Carrinho do cliente; criado vazio no primeiro acesso. */
    fun buscarOuCriar(customerId: Int): Cart =
        cartRepository.findByCustomerId(customerId)
            ?: transactionTemplate.execute { cartRepository.save(Cart().apply { this.customerId = customerId }) }!!

    fun itens(cart: Cart): List<CartItem> = cartItemRepository.findAllByCartIdOrderByIdAsc(cart.id!!)

    fun ver(customerId: Int): CarrinhoComProdutos {
        val cart = buscarOuCriar(customerId)
        val itens = itens(cart)
        return CarrinhoComProdutos(cart, itens, produtosClient.buscarPorIds(itens.map { it.productId }))
    }

    /** Produto já no carrinho: soma a quantidade. */
    fun adicionar(customerId: Int, productId: Int, quantity: Int): CarrinhoComProdutos {
        val cart = buscarOuCriar(customerId)
        val produto = buscarProdutoAtivo(productId)
        val existente = cartItemRepository.findByCartIdAndProductId(cart.id!!, productId)
        val total = (existente?.quantity ?: 0) + quantity
        conferirEstoque(produto, total, existente?.quantity ?: 0)

        transactionTemplate.executeWithoutResult {
            cartItemRepository.save(
                (existente ?: CartItem().apply { this.cartId = cart.id!!; this.productId = productId })
                    .apply { this.quantity = total }
            )
        }
        return ver(customerId)
    }

    /** Troca a quantidade do item (valor final, não soma). */
    fun alterar(customerId: Int, productId: Int, quantity: Int): CarrinhoComProdutos {
        val cart = buscarOuCriar(customerId)
        val item = buscarItem(cart, productId)
        conferirEstoque(buscarProdutoAtivo(productId), quantity, null)
        transactionTemplate.executeWithoutResult { cartItemRepository.save(item.apply { this.quantity = quantity }) }
        return ver(customerId)
    }

    fun remover(customerId: Int, productId: Int) {
        val cart = buscarOuCriar(customerId)
        val item = buscarItem(cart, productId)
        transactionTemplate.executeWithoutResult { cartItemRepository.delete(item) }
    }

    private fun buscarItem(cart: Cart, productId: Int): CartItem =
        cartItemRepository.findByCartIdAndProductId(cart.id!!, productId)
            ?: throw EntityNotFoundException("Produto id=$productId não está no carrinho")

    private fun buscarProdutoAtivo(productId: Int): ProdutoInterno {
        val produto = produtosClient.buscarPorIds(listOf(productId))[productId]
            ?: throw EntityNotFoundException("Produto id=$productId não encontrado")
        if (!produto.active) {
            throw ConflitoException("Produto id=$productId não está mais disponível", mapOf("$productId" to "produto inativo"))
        }
        return produto
    }

    private fun conferirEstoque(produto: ProdutoInterno, quantidadeFinal: Int, jaNoCarrinho: Int?) {
        if (quantidadeFinal > produto.stock) {
            val detalhe = "pedido $quantidadeFinal" + (jaNoCarrinho?.takeIf { it > 0 }?.let { " ($it já no carrinho)" } ?: "") +
                ", disponível ${produto.stock}"
            throw ConflitoException(
                "Quantidade acima do estoque de '${produto.name}'",
                mapOf("${produto.id}" to "estoque insuficiente: $detalhe")
            )
        }
    }
}
