package com.raizpet.api.service

import com.raizpet.api.entity.Product
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.exception.EstoqueInsuficienteException
import com.raizpet.api.repository.ProductRepository
import com.raizpet.api.web.dto.ItemEstoqueDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Reserva e estorno de estoque, chamados pelo service-pedidos no checkout
 * (compensação simplificada, ver service-pedidos/README.md).
 *
 * Os dois são "tudo ou nada" numa transação LOCAL deste serviço: as linhas dos produtos ficam
 * travadas (SELECT ... FOR UPDATE) enquanto tudo é conferido, e só depois de todos os itens
 * passarem é que o estoque muda. Qualquer exceção desfaz a transação inteira.
 *
 * Sem idempotência (decisão aceita): reenviar o mesmo estorno devolve o estoque duas vezes.
 */
@Service
class EstoqueService(private val productRepository: ProductRepository) {

    private val log = LoggerFactory.getLogger(EstoqueService::class.java)

    @Transactional
    fun reservar(itens: List<ItemEstoqueDto>): List<Product> {
        val quantidades = somarPorProduto(itens)
        val produtos = travarProdutos(quantidades.keys)

        val falhas = linkedMapOf<String, String>()
        produtos.forEach { p ->
            val pedida = quantidades.getValue(p.id!!)
            when {
                !p.active -> falhas[p.id.toString()] = "produto inativo"
                p.stock < pedida -> falhas[p.id.toString()] = "estoque insuficiente: pedido $pedida, disponível ${p.stock}"
            }
        }
        if (falhas.isNotEmpty()) {
            log.warn("Reserva recusada, nada foi baixado: {}", falhas)
            throw EstoqueInsuficienteException(falhas)
        }

        produtos.forEach { it.stock -= quantidades.getValue(it.id!!) }
        log.info("Reserva feita: {}", quantidades)
        return produtos
    }

    @Transactional
    fun estornar(itens: List<ItemEstoqueDto>): List<Product> {
        val quantidades = somarPorProduto(itens)
        val produtos = travarProdutos(quantidades.keys)
        // Produto inativo também recebe a devolução: o estoque reservado era dele
        produtos.forEach { it.stock += quantidades.getValue(it.id!!) }
        log.info("Estorno feito: {}", quantidades)
        return produtos
    }

    private fun somarPorProduto(itens: List<ItemEstoqueDto>): Map<Int, Int> =
        itens.groupingBy { it.productId!! }.fold(0) { soma, item -> soma + item.quantity!! }

    // Produto que não existe: 404 e nada muda (a exceção acontece antes de qualquer alteração)
    private fun travarProdutos(ids: Set<Int>): List<Product> {
        val produtos = productRepository.findAllByIdInForUpdate(ids)
        val faltando = ids - produtos.mapNotNull { it.id }.toSet()
        if (faltando.isNotEmpty()) {
            throw EntityNotFoundException("Produto(s) não encontrado(s): ${faltando.sorted().joinToString(", ")}")
        }
        return produtos
    }
}
