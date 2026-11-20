package com.raizpet.api.service

import com.raizpet.api.client.ProdutoInterno
import com.raizpet.api.client.ProdutosClient
import com.raizpet.api.entity.Favorite
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.FavoriteRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** Favorito com os dados atuais do produto (null se o produto não existe mais no service-produtos). */
data class FavoritoComProduto(val favorite: Favorite, val produto: ProdutoInterno?)

/** Resultado do POST: [criado] = false quando o produto já era favorito (idempotente). */
data class FavoritoAdicionado(val favorito: FavoritoComProduto, val criado: Boolean)

/**
 * Favoritos do cliente. A tabela guarda só customer_id + product_id; nome, preço e foto vêm do
 * service-produtos (GET /interno/produtos?ids=), UMA chamada para a lista inteira.
 *
 * Mesmo padrão do CarrinhoService: a chamada HTTP fica fora de transação, e a escrita usa um
 * TransactionTemplate curto. Service-produtos fora do ar = 503 (ServicoIndisponivelException).
 */
@Service
class FavoritoService(
    private val favoriteRepository: FavoriteRepository,
    private val produtosClient: ProdutosClient,
    private val transactionTemplate: TransactionTemplate
) {

    private val log = LoggerFactory.getLogger(FavoritoService::class.java)

    fun listar(customerId: Int): List<FavoritoComProduto> {
        val favoritos = favoriteRepository.findAllByCustomerIdOrderByCreatedAtDescIdDesc(customerId)
        val produtos = produtosClient.buscarPorIds(favoritos.map { it.productId })
        return favoritos.map { FavoritoComProduto(it, produtos[it.productId]) }
    }

    /**
     * Idempotente: favoritar de novo devolve o favorito que já existe (criado = false).
     * Produto inexistente = 404; inativo = 409 (mesma regra do carrinho).
     */
    fun adicionar(customerId: Int, productId: Int): FavoritoAdicionado {
        val produto = produtosClient.buscarPorIds(listOf(productId))[productId]
            ?: throw EntityNotFoundException("Produto id=$productId não encontrado")

        favoriteRepository.findByCustomerIdAndProductId(customerId, productId)
            ?.let { return FavoritoAdicionado(FavoritoComProduto(it, produto), criado = false) }

        if (!produto.active) {
            throw ConflitoException("Produto id=$productId não está mais disponível", mapOf("$productId" to "produto inativo"))
        }

        return try {
            val novo = transactionTemplate.execute {
                favoriteRepository.save(Favorite().apply { this.customerId = customerId; this.productId = productId })
            }!!
            FavoritoAdicionado(FavoritoComProduto(novo, produto), criado = true)
        } catch (ex: DataIntegrityViolationException) {
            // Dois POST ao mesmo tempo: o segundo bate na uk_favorite; o favorito já existe, então segue idempotente
            log.info("Favorito {}/{} criado por outra requisição simultânea", customerId, productId)
            val existente = favoriteRepository.findByCustomerIdAndProductId(customerId, productId) ?: throw ex
            FavoritoAdicionado(FavoritoComProduto(existente, produto), criado = false)
        }
    }

    /** Idempotente: se o produto não era favorito, não faz nada. Não chama o service-produtos. */
    fun remover(customerId: Int, productId: Int) {
        transactionTemplate.executeWithoutResult {
            favoriteRepository.findByCustomerIdAndProductId(customerId, productId)?.let(favoriteRepository::delete)
        }
    }
}
