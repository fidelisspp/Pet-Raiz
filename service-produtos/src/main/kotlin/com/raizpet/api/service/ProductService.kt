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

    // ---- Área do produtor: tudo filtrado pelo id do token. Produto de outro produtor ou já
    // removido dá 404 (e não 403), para não revelar que o id existe. ----

    @Transactional(readOnly = true)
    fun buscarMeus(producerId: Int, pageable: Pageable): Page<Product> =
        productRepository.findAllByProducerIdAndActiveTrue(producerId, pageable)

    @Transactional(readOnly = true)
    fun buscarMeu(id: Int, producerId: Int): Product =
        productRepository.findByIdAndProducerIdAndActiveTrue(id, producerId)
            ?: throw EntityNotFoundException("Produto id=$id não encontrado")

    /** [alterar] copia os dados novos para o produto (ProdutoMapper.atualizar); o save sai no commit. */
    @Transactional
    fun atualizarMeu(id: Int, producerId: Int, categoryId: Int, alterar: (Product) -> Unit): Product {
        val product = buscarMeu(id, producerId)
        alterar(product)
        product.category = categoryService.buscarPorId(categoryId)
        return product
    }

    /**
     * Soft delete: active = false. O produto some do catálogo e do /meus, mas continua no banco
     * (pedidos antigos e carrinhos ainda apontam para o id; no carrinho ele aparece como available = false).
     */
    @Transactional
    fun removerMeu(id: Int, producerId: Int) {
        buscarMeu(id, producerId).active = false
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

    // ---- Admin (Etapa 5.4) ----

    /**
     * Produtos publicados (o mesmo recorte do catálogo) com a busca do admin, que também procura no
     * nome do produtor: o nome vem da lista de aprovados do service-usuarios (uma chamada, como o catálogo).
     */
    fun buscarParaAdmin(busca: String?, categoriaId: Int?, pageable: Pageable): Page<ProdutoDoCatalogo> {
        val aprovados = usuariosClient.buscarProdutoresAprovados()
        if (aprovados.isEmpty()) return Page.empty(pageable)
        val termo = busca?.trim()?.takeIf { it.isNotEmpty() }
        val produtoresDaBusca = termo?.let { t ->
            aprovados.filterValues { it.companyName.contains(t, ignoreCase = true) }.keys
        }.orEmpty().ifEmpty { setOf(-1) }
        return productRepository
            .findParaAdmin(aprovados.keys, termo, produtoresDaBusca, categoriaId, pageable)
            .map { ProdutoDoCatalogo(it, aprovados.getValue(it.producerId)) }
    }

    /** (produtos publicados, produtores com pelo menos um publicado) para o resumo do topo. */
    fun resumoParaAdmin(): Pair<Long, Long> {
        val aprovados = usuariosClient.buscarProdutoresAprovados()
        if (aprovados.isEmpty()) return 0L to 0L
        return productRepository.contarPublicados(aprovados.keys) to
            productRepository.contarProdutoresComPublicados(aprovados.keys)
    }

    /**
     * O admin tira o produto da loja: o mesmo soft delete do produtor (active = false).
     * Pedidos antigos e carrinhos continuam apontando para o id; no carrinho ele vira available = false.
     */
    @Transactional
    fun removerComoAdmin(id: Int) {
        val product = productRepository.findByIdAndActiveTrue(id)
            ?: throw EntityNotFoundException("Produto id=$id não encontrado")
        product.active = false
    }

    @Transactional(readOnly = true)
    fun buscarPorIds(ids: Collection<Int>): List<Product> =
        if (ids.isEmpty()) emptyList() else productRepository.findAllByIdIn(ids)
}
