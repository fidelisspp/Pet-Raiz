package com.raizpet.api.service

import com.raizpet.api.entity.Category
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.CategoryRepository
import com.raizpet.api.repository.ProductRepository
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository
) {

    companion object {
        const val MSG_NOME_REPETIDO = "Já existe uma categoria com esse nome"
    }

    @Transactional(readOnly = true)
    fun buscarTodas(): List<Category> = categoryRepository.findAll(Sort.by("name"))

    @Transactional(readOnly = true)
    fun buscarPorId(id: Int): Category =
        categoryRepository.findByIdOrNull(id)
            ?: throw EntityNotFoundException("Categoria id=$id não encontrada")

    // ---- Admin (Etapa 5.2) ----

    /** Categoria -> (ativos, removidos); categoria sem produto nenhum fica com (0, 0). */
    @Transactional(readOnly = true)
    fun contagens(): Map<Int, Pair<Long, Long>> =
        productRepository.contarPorCategoria().associate { linha ->
            (linha[0] as Number).toInt() to ((linha[1] as Number).toLong() to (linha[2] as Number).toLong())
        }

    @Transactional
    fun criar(nome: String): Category {
        val limpo = nome.trim()
        if (categoryRepository.existsByNameIgnoreCase(limpo)) throw ConflitoException(MSG_NOME_REPETIDO)
        return categoryRepository.save(Category().apply { name = limpo })
    }

    /** Renomear não mexe nos produtos: eles guardam o id da categoria, e o nome novo já aparece no catálogo. */
    @Transactional
    fun renomear(id: Int, nome: String): Category {
        val categoria = buscarPorId(id)
        val limpo = nome.trim()
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(limpo, id)) throw ConflitoException(MSG_NOME_REPETIDO)
        categoria.name = limpo
        return categoria
    }

    /**
     * Só categoria vazia: a FK product.category_id não deixa apagar se houver qualquer produto,
     * inclusive os removidos pelo produtor (soft delete). A mensagem diz qual dos dois casos é.
     */
    @Transactional
    fun excluir(id: Int) {
        val categoria = buscarPorId(id)
        val ativos = productRepository.countByCategoryIdAndActive(id, true)
        val removidos = productRepository.countByCategoryIdAndActive(id, false)
        when {
            ativos > 0 -> throw ConflitoException(
                "A categoria \"${categoria.name}\" tem $ativos produto(s) à venda. " +
                    "Ela só pode ser excluída depois que os produtores trocarem esses produtos de categoria."
            )
            removidos > 0 -> throw ConflitoException(
                "A categoria \"${categoria.name}\" não tem produtos à venda, mas ainda guarda $removidos produto(s) " +
                    "removido(s) pelos produtores, que continuam nos pedidos antigos. Por isso ela não pode ser excluída; " +
                    "se quiser, renomeie a categoria."
            )
        }
        categoryRepository.delete(categoria)
    }
}
