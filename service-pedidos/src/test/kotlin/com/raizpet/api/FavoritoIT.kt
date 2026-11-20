package com.raizpet.api

import com.raizpet.api.web.dto.FavoritoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import java.util.function.Consumer

/**
 * Favoritos (/api/v1/favoritos). Dados dos produtos vêm do ProdutosStub:
 * 100, 101, 102 e 106 ativos; 103 inativo; outros ids não existem.
 */
class FavoritoIT : IntegracaoBase() {

    private val lista = object : ParameterizedTypeReference<List<FavoritoResponseDto>>() {}

    private fun favoritar(productId: Int?, quem: Consumer<HttpHeaders> = Tokens.clienteBia()) =
        post("/api/v1/favoritos", quem, if (productId == null) "{}" else """{"productId":$productId}""")

    private fun favoritos(quem: Consumer<HttpHeaders> = Tokens.clienteBia()): List<FavoritoResponseDto> =
        testClient.get().uri("/api/v1/favoritos").headers(quem).exchange()
            .expectStatus().isOk.expectBody(lista).returnResult().responseBody!!

    private fun desfavoritar(productId: Int, quem: Consumer<HttpHeaders>? = Tokens.clienteBia()) =
        testClient.delete().uri("/api/v1/favoritos/$productId").apply { if (quem != null) headers(quem) }.exchange()

    // ---------- GET ----------

    @Test
    fun listar_SemFavoritos_RetornarListaVaziaSemChamarProdutos() {
        assertThat(favoritos()).isEmpty()
        assertThat(ProdutosStub.consultas.get()).isZero()
    }

    @Test
    fun listar_ComFavoritos_MaisRecentesPrimeiroComDadosDoProdutoNumaUnicaChamada() {
        listOf(100, 101, 106).forEach { favoritar(it).expectStatus().isCreated }
        ProdutosStub.consultas.set(0)

        val itens = favoritos()

        assertThat(itens.map { it.productId }).containsExactly(106, 101, 100)
        val mel = itens[0]
        assertThat(mel.name).isEqualTo("Mel para Cães")
        assertThat(mel.price).isEqualByComparingTo("30.00")
        assertThat(mel.stock).isEqualTo(5)
        assertThat(mel.imageUrl).isEqualTo("/images/mel.jpg")
        assertThat(mel.producerId).isEqualTo(106)
        assertThat(mel.available).isTrue()
        assertThat(mel.createdAt).isNotNull()
        // Uma chamada ao /interno/produtos?ids= para a lista inteira
        assertThat(ProdutosStub.consultas.get()).isEqualTo(1)
    }

    @Test
    fun listar_ProdutoDesativadoOuApagadoDepois_ContinuaNaListaComAvailableFalse() {
        favoritar(100).expectStatus().isCreated
        favoritar(101).expectStatus().isCreated

        ProdutosStub.produtos[100]!!.active = false   // produtor removeu (soft delete)
        ProdutosStub.produtos.remove(101)             // não existe mais

        val itens = favoritos().associateBy { it.productId }
        assertThat(itens[100]!!.available).isFalse()
        assertThat(itens[100]!!.name).isEqualTo("Ração Orgânica de Frango")
        assertThat(itens[101]!!.available).isFalse()
        assertThat(itens[101]!!.name).isNull()
        assertThat(itens[101]!!.price).isNull()
    }

    @Test
    fun listar_SoOsDoClienteDoToken() {
        jdbc.update("INSERT INTO favorite (customer_id, product_id) VALUES (102, 100)")   // do Bob
        assertThat(favoritos()).isEmpty()
        assertThat(favoritos(Tokens.clienteBobSuspenso()).map { it.productId }).containsExactly(100)
    }

    // ---------- POST ----------

    @Test
    fun favoritar_ProdutoAtivo_Retornar201ComDadosDoProduto() {
        val f = favoritar(100).expectStatus().isCreated
            .expectBody(FavoritoResponseDto::class.java).returnResult().responseBody!!
        assertThat(f.productId).isEqualTo(100)
        assertThat(f.name).isEqualTo("Ração Orgânica de Frango")
        assertThat(f.available).isTrue()
        assertThat(contar("favorite")).isEqualTo(1)
    }

    @Test
    fun favoritar_DuasVezes_Idempotente200SemDuplicar() {
        val primeiro = favoritar(100).expectStatus().isCreated
            .expectBody(FavoritoResponseDto::class.java).returnResult().responseBody!!
        val segundo = favoritar(100).expectStatus().isOk
            .expectBody(FavoritoResponseDto::class.java).returnResult().responseBody!!

        assertThat(segundo).isEqualTo(primeiro)
        assertThat(contar("favorite")).isEqualTo(1)
    }

    @Test
    fun favoritar_ProdutoComEstoqueZero_Permitido() {
        favoritar(102).expectStatus().isCreated
    }

    @Test
    fun favoritar_ProdutoInexistente_RetornarErrorMessageStatus404() {
        esperarErro(favoritar(9999), 404)
        assertThat(contar("favorite")).isZero()
    }

    @Test
    fun favoritar_ProdutoInativo_RetornarErrorMessageStatus409() {
        val erro = esperarErro(favoritar(103), 409)
        assertThat(erro.errors).containsEntry("103", "produto inativo")
        assertThat(contar("favorite")).isZero()
    }

    @Test
    fun favoritar_JaFavoritoQueFoiDesativado_Continua200() {
        favoritar(100).expectStatus().isCreated
        ProdutosStub.produtos[100]!!.active = false
        // Já era favorito: idempotente, não vira 409
        favoritar(100).expectStatus().isOk
    }

    @Test
    fun favoritar_ComCorpoInvalido_RetornarErrorMessageStatus422Ou400() {
        esperarErro(favoritar(null), 422)
        esperarErro(favoritar(0), 422)
        esperarErro(post("/api/v1/favoritos", Tokens.clienteBia(), "{ isto não é json"), 400)
    }

    // ---------- DELETE ----------

    @Test
    fun desfavoritar_Favorito_Retornar204ESairDaLista() {
        favoritar(100).expectStatus().isCreated
        favoritar(101).expectStatus().isCreated
        ProdutosStub.consultas.set(0)

        desfavoritar(100).expectStatus().isNoContent

        // Não chamou o service-produtos
        assertThat(ProdutosStub.consultas.get()).isZero()
        assertThat(favoritos().map { it.productId }).containsExactly(101)
    }

    @Test
    fun desfavoritar_QueNaoEraFavorito_Idempotente204() {
        desfavoritar(100).expectStatus().isNoContent
        desfavoritar(9999).expectStatus().isNoContent
    }

    @Test
    fun desfavoritar_NaoMexeNosFavoritosDeOutroCliente() {
        jdbc.update("INSERT INTO favorite (customer_id, product_id) VALUES (102, 100)")   // do Bob
        desfavoritar(100).expectStatus().isNoContent                                    // pela Bia
        assertThat(contar("favorite")).isEqualTo(1)
    }

    // ---------- service-produtos fora do ar ----------

    @Test
    fun produtosForaDoAr_ListarEFavoritar503_DesfavoritarFunciona() {
        favoritar(100).expectStatus().isCreated
        ProdutosStub.modo = ProdutosStub.Modo.ERRO_500

        esperarErro(testClient.get().uri("/api/v1/favoritos").headers(Tokens.clienteBia()).exchange(), 503)
        esperarErro(favoritar(101), 503)
        desfavoritar(100).expectStatus().isNoContent
        assertThat(contar("favorite")).isZero()
    }

    // ---------- Segurança ----------

    @Test
    fun rotasFavoritos_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/favoritos").exchange(), 401)
        esperarErro(desfavoritar(100, null), 401)
    }

    @Test
    fun rotasFavoritos_ComProdutorOuAdmin_RetornarErrorMessageStatus403() {
        listOf(Tokens.produtorCarlos(), Tokens.adminAna()).forEach { quem ->
            esperarErro(testClient.get().uri("/api/v1/favoritos").headers(quem).exchange(), 403)
            esperarErro(favoritar(100, quem), 403)
            esperarErro(desfavoritar(100, quem), 403)
        }
        assertThat(contar("favorite")).isZero()
    }
}
