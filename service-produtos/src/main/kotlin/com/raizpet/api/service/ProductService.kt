package com.raizpet.api.service

import com.raizpet.api.client.ProdutorAprovado
import com.raizpet.api.client.UsuariosClient
import com.raizpet.api.entity.PetType
import com.raizpet.api.entity.Product
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Produto do catálogo junto com o produtor aprovado que veio do service-usuarios. */
data class ProdutoDoCatalogo(val product: Product, val produtor: ProdutorAprovado)

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val categoryService: CategoryService,
    private val usuariosClient: UsuariosClient
) {

    /**
     * Sem consulta ao service-usuarios: o dono é o id do token, e o token só tem role PRODUCER
     * se o service-usuarios deixou o produtor logar (ou seja, aprovado na hora do login).
     */
    @Transactional
    fun salvar(product: Product, producerId: Int, categoryId: Int): Product {
        product.producerId = producerId
        product.category = categoryService.buscarPorId(categoryId)
        product.active = true
        return productRepository.save(product)
    }

    /**
     * Catálogo: UMA chamada ao service-usuarios (aprovados), depois a consulta local com
     * producer_id IN (aprovados). Se o service-usuarios falhar, ServicoIndisponivelException (503).
     * Não usa @Transactional para não segurar conexão do banco durante a chamada HTTP.
     */
    fun buscarCatalogo(
        nome: String?,
        categoriaId: Int?,
        produtorId: Int?,
        petType: PetType?,
        pageable: Pageable
    ): Page<ProdutoDoCatalogo> {
        val aprovados = usuariosClient.buscarProdutoresAprovados()
        // Nenhum aprovado, ou filtro por um produtor que não está aprovado: página vazia sem ir ao banco
        if (aprovados.isEmpty() || (produtorId != null && produtorId !in aprovados)) {
            return Page.empty(pageable)
        }
        return productRepository
            .findCatalog(aprovados.keys, nome?.trim()?.takeIf { it.isNotEmpty() }, categoriaId, produtorId, petType, pageable)
            .map { ProdutoDoCatalogo(it, aprovados.getValue(it.producerId)) }
    }

    fun buscarNoCatalogoPorId(id: Int): ProdutoDoCatalogo {
        val aprovados = usuariosClient.buscarProdutoresAprovados()
        val product = productRepository.findActiveById(id)
        val produtor = product?.let { aprovados[it.producerId] }
            ?: throw EntityNotFoundException("Produto id=$id não encontrado")
        return ProdutoDoCatalogo(product, produtor)
    }

    @Transactional(readOnly = true)
    fun buscarPorIds(ids: Collection<Int>): List<Product> =
        if (ids.isEmpty()) emptyList() else productRepository.findAllByIdIn(ids)
}
