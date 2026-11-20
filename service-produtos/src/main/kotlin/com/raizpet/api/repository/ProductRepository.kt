package com.raizpet.api.repository

import com.raizpet.api.entity.PetType
import com.raizpet.api.entity.Product
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, Int> {

    /**
     * Catálogo público. [aprovados] são os ids de produtor aprovados que vieram do service-usuarios
     * (nunca vazia: o ProductService já devolve página vazia antes de chegar aqui).
     * petType: dog traz dog + both, cat traz cat + both, both traz só both.
     */
    @EntityGraph(attributePaths = ["category"])
    @Query(
        """
        SELECT p FROM Product p
        WHERE p.active = true
          AND p.producerId IN :aprovados
          AND (:nome IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :nome, '%')))
          AND (:categoriaId IS NULL OR p.category.id = :categoriaId)
          AND (:produtorId IS NULL OR p.producerId = :produtorId)
          AND (:petType IS NULL OR p.petType = :petType OR p.petType = com.raizpet.api.entity.PetType.both)
        """
    )
    fun findCatalog(
        aprovados: Collection<Int>,
        nome: String?,
        categoriaId: Int?,
        produtorId: Int?,
        petType: PetType?,
        pageable: Pageable
    ): Page<Product>

    @EntityGraph(attributePaths = ["category"])
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.active = true")
    fun findActiveById(id: Int): Product?

    // ---- Área do produtor (/produtos/meus): só os produtos do dono do token, sem os removidos ----
    // Não filtra estoque nem aprovação: o produtor vê tudo o que é dele, inclusive estoque 0.

    @EntityGraph(attributePaths = ["category"])
    fun findAllByProducerIdAndActiveTrue(producerId: Int, pageable: Pageable): Page<Product>

    @EntityGraph(attributePaths = ["category"])
    fun findByIdAndProducerIdAndActiveTrue(id: Int, producerId: Int): Product?

    // ---- Categorias do admin (Etapa 5.2) ----

    /**
     * Por categoria: [0] id da categoria, [1] produtos ativos, [2] produtos removidos (soft delete).
     * Categoria sem nenhum produto não aparece (o CategoryService completa com zero).
     */
    @Query(
        """
        SELECT p.category.id,
               SUM(CASE WHEN p.active = true THEN 1 ELSE 0 END),
               SUM(CASE WHEN p.active = false THEN 1 ELSE 0 END)
        FROM Product p GROUP BY p.category.id
        """
    )
    fun contarPorCategoria(): List<Array<Any>>

    /** Ativos e removidos: a FK product.category_id impede excluir a categoria se houver qualquer um. */
    fun countByCategoryIdAndActive(categoryId: Int, active: Boolean): Long

    /** Rota interna de consulta: devolve o que existir (ativo ou não), sem olhar o produtor. */
    @EntityGraph(attributePaths = ["category"])
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    fun findAllByIdIn(ids: Collection<Int>): List<Product>

    /**
     * Reserva e estorno: SELECT ... FOR UPDATE nas linhas dos produtos, em ordem de id
     * (duas reservas simultâneas travam as linhas na mesma ordem, o que evita deadlock).
     * A trava dura até o fim da transação do EstoqueService.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    fun findAllByIdInForUpdate(ids: Collection<Int>): List<Product>
}
