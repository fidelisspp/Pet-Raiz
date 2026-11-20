package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutoCreateDto
import com.raizpet.api.web.dto.ProdutoCriadoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.util.function.Consumer

/**
 * Área do produtor: /api/v1/produtos/meus.
 * Seed: Carlos (103) tem 100, 101, 102 (estoque 0) e 103 (removido); Davi (106) tem o 106.
 */
class ProdutoMeuIT : IntegracaoBase() {

    private val davi = Tokens.header(Tokens.criar(106, "PRODUCER", "davi@email.com"))

    private fun alteracao() = ProdutoCreateDto(
        name = "Ração de Frango Premium",
        description = "Nova receita",
        price = BigDecimal("95.00"),
        stock = 7,
        imageUrl = "/images/frango-premium.jpg",
        categoryId = 20,
        petType = "both"
    )

    private fun get(uri: String, auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()) =
        testClient.get().uri(uri).apply { if (auth != null) headers(auth) }.exchange()

    private fun put(id: Int, dto: Any, auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()) = testClient
        .put().uri("/api/v1/produtos/meus/$id")
        .apply { if (auth != null) headers(auth) }
        .contentType(MediaType.APPLICATION_JSON)
        .body(dto)
        .exchange()

    private fun delete(id: Int, auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()) =
        testClient.delete().uri("/api/v1/produtos/meus/$id").apply { if (auth != null) headers(auth) }.exchange()

    private fun pagina(uri: String, auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()): PageableDto =
        get(uri, auth).expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!

    @Suppress("UNCHECKED_CAST")
    private fun PageableDto.ids(): List<Int> = content.map { (it as Map<String, Any>)["id"] as Int }

    private fun meu(id: Int, auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()): ProdutoCriadoResponseDto =
        get("/api/v1/produtos/meus/$id", auth).expectStatus().isOk
            .expectBody(ProdutoCriadoResponseDto::class.java).returnResult().responseBody!!

    // ---------- GET /produtos/meus ----------

    @Test
    fun listarMeus_ComTokenDeProdutor_RetornarSoOsDeleIncluindoEstoqueZeroSemRemovidos() {
        val pagina = pagina("/api/v1/produtos/meus")

        // Mais recentes primeiro (empate de createdAt desempata por id desc). 103 foi removido; 106 é do Davi.
        assertThat(pagina.ids()).containsExactly(102, 101, 100)
        assertThat(pagina.totalElements).isEqualTo(3)

        @Suppress("UNCHECKED_CAST")
        val semEstoque = pagina.content[0] as Map<String, Any?>
        assertThat(semEstoque["stock"]).isEqualTo(0)
        assertThat(semEstoque["active"]).isEqualTo(true)
        assertThat(semEstoque["producerId"]).isEqualTo(103)

        // Não consulta o service-usuarios
        assertThat(UsuariosStub.chamadas.get()).isZero()
    }

    @Test
    fun listarMeus_ComPaginacao_RetornarPaginaSolicitada() {
        val pagina = pagina("/api/v1/produtos/meus?page=1&size=2")
        assertThat(pagina.ids()).containsExactly(100)
        assertThat(pagina.totalPages).isEqualTo(2)
        assertThat(pagina.totalElements).isEqualTo(3)
    }

    @Test
    fun listarMeus_ProdutorSemProdutos_RetornarPaginaVazia() {
        val pagina = pagina("/api/v1/produtos/meus", Tokens.header(Tokens.criar(999, "PRODUCER")))
        assertThat(pagina.content).isEmpty()
        assertThat(pagina.totalElements).isZero()
    }

    @Test
    fun listarMeus_ComServicoDeUsuariosFora_ContinuaFuncionando() {
        UsuariosStub.modo = UsuariosStub.Modo.ERRO_500
        assertThat(pagina("/api/v1/produtos/meus").ids()).containsExactly(102, 101, 100)
    }

    // ---------- GET /produtos/meus/{id} ----------

    @Test
    fun buscarMeu_ProdutoDoDono_RetornarProdutoComStatus200() {
        val produto = meu(100)
        assertThat(produto.name).isEqualTo("Ração Orgânica de Frango")
        assertThat(produto.imageUrl).isEqualTo("/images/frango.jpg")
        assertThat(produto.petType).isEqualTo("dog")
        assertThat(produto.category.id).isEqualTo(10)
        assertThat(produto.producerId).isEqualTo(103)
    }

    @Test
    fun buscarMeu_DeOutroProdutorRemovidoOuInexistente_RetornarErrorMessageStatus404() {
        esperarErro(get("/api/v1/produtos/meus/106"), 404)   // do Davi
        esperarErro(get("/api/v1/produtos/meus/103"), 404)   // removido
        esperarErro(get("/api/v1/produtos/meus/9999"), 404)  // não existe
    }

    @Test
    fun buscarMeu_ComIdNaoNumerico_RetornarErrorMessageStatus400() {
        esperarErro(get("/api/v1/produtos/meus/abc"), 400)
    }

    // ---------- PUT /produtos/meus/{id} ----------

    @Test
    fun alterarMeu_ComDadosValidos_RetornarProdutoAlteradoEAtualizarCatalogo() {
        val produto = put(100, alteracao())
            .expectStatus().isOk
            .expectBody(ProdutoCriadoResponseDto::class.java).returnResult().responseBody!!

        assertThat(produto.id).isEqualTo(100)
        assertThat(produto.name).isEqualTo("Ração de Frango Premium")
        assertThat(produto.description).isEqualTo("Nova receita")
        assertThat(produto.price).isEqualByComparingTo("95.00")
        assertThat(produto.stock).isEqualTo(7)
        assertThat(produto.imageUrl).isEqualTo("/images/frango-premium.jpg")
        assertThat(produto.petType).isEqualTo("both")
        assertThat(produto.category.name).isEqualTo("Petiscos")
        assertThat(produto.producerId).isEqualTo(103)
        assertThat(produto.active).isTrue()

        // Gravou de verdade e o catálogo público já mostra o preço novo
        assertThat(meu(100).price).isEqualByComparingTo("95.00")
        get("/api/v1/produtos/100", auth = null).expectStatus().isOk
            .expectBody().jsonPath("$.price").isEqualTo(95.0)
    }

    @Test
    fun alterarMeu_SemImageUrlESemPetType_ManterFotoETipoAtuais() {
        val produto = put(100, alteracao().copy(imageUrl = null, petType = null))
            .expectStatus().isOk
            .expectBody(ProdutoCriadoResponseDto::class.java).returnResult().responseBody!!

        assertThat(produto.name).isEqualTo("Ração de Frango Premium")
        assertThat(produto.imageUrl).isEqualTo("/images/frango.jpg")
        assertThat(produto.petType).isEqualTo("dog")
    }

    @Test
    fun alterarMeu_ParaEstoqueZero_ContinuaNaListaDoProdutor() {
        put(101, alteracao().copy(stock = 0)).expectStatus().isOk
        assertThat(pagina("/api/v1/produtos/meus").ids()).contains(101)
    }

    @Test
    fun alterarMeu_DeOutroProdutorRemovidoOuInexistente_RetornarErrorMessageStatus404() {
        esperarErro(put(106, alteracao()), 404)
        esperarErro(put(103, alteracao()), 404)
        esperarErro(put(9999, alteracao()), 404)

        // O produto do Davi não mudou
        assertThat(meu(106, davi).name).isEqualTo("Mel para Cães")
    }

    @Test
    fun alterarMeu_ComCategoriaInexistente_RetornarErrorMessageStatus404() {
        esperarErro(put(100, alteracao().copy(categoryId = 999)), 404)
        assertThat(meu(100).category.id).isEqualTo(10)
    }

    @Test
    fun alterarMeu_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        listOf(
            alteracao().copy(name = ""),
            alteracao().copy(price = BigDecimal.ZERO),
            alteracao().copy(price = null),
            alteracao().copy(stock = -1),
            alteracao().copy(stock = null),
            alteracao().copy(imageUrl = ""),
            alteracao().copy(imageUrl = "https://exemplo.com/x.jpg"),
            alteracao().copy(categoryId = null),
            alteracao().copy(petType = "peixe")
        ).forEach { esperarErro(put(100, it), 422) }

        assertThat(meu(100).name).isEqualTo("Ração Orgânica de Frango")
    }

    // ---------- DELETE /produtos/meus/{id} ----------

    @Test
    fun removerMeu_ProdutoDoDono_SoftDeleteSomeDoCatalogoEDaLista() {
        delete(100).expectStatus().isNoContent

        esperarErro(get("/api/v1/produtos/meus/100"), 404)
        assertThat(pagina("/api/v1/produtos/meus").ids()).containsExactly(102, 101)
        esperarErro(get("/api/v1/produtos/100", auth = null), 404)

        // Soft delete: a linha continua no banco, só inativa (a rota interna mostra active = false)
        get("/api/v1/interno/produtos?ids=100", auth = Consumer { it.add("X-Internal-Key", UsuariosStub.CHAVE) })
            .expectStatus().isOk
            .expectBody().jsonPath("$[0].active").isEqualTo(false)

        // Remover de novo: 404
        esperarErro(delete(100), 404)
    }

    @Test
    fun removerMeu_DeOutroProdutorOuInexistente_RetornarErrorMessageStatus404() {
        esperarErro(delete(106), 404)
        esperarErro(delete(9999), 404)
        // O produto do Davi continua no catálogo
        get("/api/v1/produtos/106", auth = null).expectStatus().isOk
    }

    // ---------- Segurança ----------

    @Test
    fun rotasMeus_SemToken_RetornarErrorMessageStatus401() {
        // Prova que a regra de /produtos/meus vem antes do permitAll de GET /produtos/*
        esperarErro(get("/api/v1/produtos/meus", auth = null), 401)
        esperarErro(get("/api/v1/produtos/meus/100", auth = null), 401)
        esperarErro(put(100, alteracao(), auth = null), 401)
        esperarErro(delete(100, auth = null), 401)
        // A chave interna não substitui o token
        esperarErro(get("/api/v1/produtos/meus", auth = Consumer { it.add("X-Internal-Key", UsuariosStub.CHAVE) }), 401)
    }

    @Test
    fun rotasMeus_ComClienteOuAdmin_RetornarErrorMessageStatus403() {
        listOf(Tokens.clienteBia(), Tokens.adminAna()).forEach { auth ->
            esperarErro(get("/api/v1/produtos/meus", auth), 403)
            esperarErro(get("/api/v1/produtos/meus/100", auth), 403)
            esperarErro(put(100, alteracao(), auth), 403)
            esperarErro(delete(100, auth), 403)
        }
        assertThat(meu(100).name).isEqualTo("Ração Orgânica de Frango")
    }
}
