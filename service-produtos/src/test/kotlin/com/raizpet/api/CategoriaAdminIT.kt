package com.raizpet.api

import com.raizpet.api.web.dto.CategoriaCreateDto
import com.raizpet.api.web.dto.CategoriaResponseDto
import com.raizpet.api.web.dto.CategoriaResumoDto
import com.raizpet.api.web.dto.ProdutoCreateDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.util.function.Consumer

/**
 * Etapa 5.2: gestão de categorias pelo ADMIN.
 * Seed: 10 "Ração Natural" (100, 102 e 104 ativos) e 20 "Petiscos" (101, 105 e 106 ativos; 103 removido).
 */
class CategoriaAdminIT : IntegracaoBase() {

    private fun resumo(auth: Consumer<HttpHeaders> = Tokens.adminAna()): List<CategoriaResumoDto> =
        testClient.get().uri("/api/v1/categorias/resumo").headers(auth)
            .exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<CategoriaResumoDto>>() {})
            .returnResult().responseBody!!

    private fun criar(nome: String?, auth: Consumer<HttpHeaders> = Tokens.adminAna()) =
        testClient.post().uri("/api/v1/categorias").headers(auth)
            .contentType(MediaType.APPLICATION_JSON).body(CategoriaCreateDto(nome)).exchange()

    private fun renomear(id: Int, nome: String?) =
        testClient.put().uri("/api/v1/categorias/$id").headers(Tokens.adminAna())
            .contentType(MediaType.APPLICATION_JSON).body(CategoriaCreateDto(nome)).exchange()

    private fun excluir(id: Int, auth: Consumer<HttpHeaders> = Tokens.adminAna()) =
        testClient.delete().uri("/api/v1/categorias/$id").headers(auth).exchange()

    private fun idCriado(nome: String): Int = criar(nome).expectStatus().isCreated
        .expectBody(CategoriaResponseDto::class.java).returnResult().responseBody!!.id

    // ---------- GET /categorias/resumo ----------

    @Test
    fun resumo_ComAdmin_ContarAtivosERemovidosEmOrdemAlfabetica() {
        assertThat(resumo()).containsExactly(
            CategoriaResumoDto(20, "Petiscos", 3, 1),
            CategoriaResumoDto(10, "Ração Natural", 3, 0)
        )
    }

    @Test
    fun resumo_CategoriaSemProdutos_ContarZero() {
        val id = idCriado("Acessórios")
        assertThat(resumo().first { it.id == id }).isEqualTo(CategoriaResumoDto(id, "Acessórios", 0, 0))
    }

    @Test
    fun resumo_ComClienteOuSemToken_Retornar403e401() {
        esperarErro(testClient.get().uri("/api/v1/categorias/resumo").headers(Tokens.clienteBia()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/categorias/resumo").exchange(), 401)
    }

    // ---------- POST /categorias ----------

    @Test
    fun criar_NomeNovo_Retornar201EAparecerNaListaPublica() {
        val criada = criar("  Suplementos  ").expectStatus().isCreated
            .expectBody(CategoriaResponseDto::class.java).returnResult().responseBody!!
        assertThat(criada.name).isEqualTo("Suplementos")

        val publica = testClient.get().uri("/api/v1/categorias").exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<CategoriaResponseDto>>() {})
            .returnResult().responseBody!!
        assertThat(publica.map { it.name }).containsExactly("Petiscos", "Ração Natural", "Suplementos")
    }

    @Test
    fun criar_NomeRepetidoSemDiferenciarMaiusculaNemAcento_Retornar409() {
        assertThat(esperarErro(criar("petiscos"), 409).message).isEqualTo("Já existe uma categoria com esse nome")
        esperarErro(criar("RACAO NATURAL"), 409)
    }

    @Test
    fun criar_NomeVazioOuLongo_Retornar422() {
        assertThat(esperarErro(criar("   "), 422).errors).containsKey("name")
        esperarErro(criar("x".repeat(101)), 422)
    }

    @Test
    fun criar_ComProdutorOuSemToken_Retornar403e401() {
        esperarErro(criar("Nova", Tokens.produtorCarlos()), 403)
        esperarErro(
            testClient.post().uri("/api/v1/categorias").contentType(MediaType.APPLICATION_JSON)
                .body(CategoriaCreateDto("Nova")).exchange(),
            401
        )
    }

    // ---------- PUT /categorias/{id} ----------

    @Test
    fun renomear_Retornar200EProdutosMostraremONomeNovo() {
        renomear(20, "Petiscos Naturais").expectStatus().isOk
        val produto = testClient.get().uri("/api/v1/produtos/101").exchange().expectStatus().isOk
            .expectBody(Map::class.java).returnResult().responseBody!!
        @Suppress("UNCHECKED_CAST")
        assertThat((produto["category"] as Map<String, Any>)["name"]).isEqualTo("Petiscos Naturais")
    }

    @Test
    fun renomear_ParaOMesmoNome_Retornar200() {
        renomear(20, "PETISCOS").expectStatus().isOk
    }

    @Test
    fun renomear_ParaNomeDeOutra_Retornar409EInexistente404() {
        esperarErro(renomear(20, "Ração Natural"), 409)
        esperarErro(renomear(999, "Qualquer"), 404)
    }

    // ---------- DELETE /categorias/{id} ----------

    @Test
    fun excluir_Vazia_Retornar204ESumirDaLista() {
        val id = idCriado("Temporária")
        excluir(id).expectStatus().isNoContent
        assertThat(resumo().map { it.id }).doesNotContain(id)
        esperarErro(excluir(id), 404)
    }

    @Test
    fun excluir_ComProdutosAtivos_Retornar409() {
        assertThat(esperarErro(excluir(10), 409).message).contains("3 produto(s) à venda")
    }

    @Test
    fun excluir_SoComProdutoRemovido_Retornar409ExplicandoOMotivo() {
        // Produto criado e depois removido pelo produtor: o soft delete deixa a linha apontando para a categoria
        val id = idCriado("Só removidos")
        val produto = testClient.post().uri("/api/v1/produtos").headers(Tokens.produtorCarlos())
            .contentType(MediaType.APPLICATION_JSON)
            .body(ProdutoCreateDto(name = "Teste", price = BigDecimal("1.00"), stock = 1, categoryId = id))
            .exchange().expectStatus().isCreated
            .expectBody(Map::class.java).returnResult().responseBody!!["id"] as Int
        testClient.delete().uri("/api/v1/produtos/meus/$produto").headers(Tokens.produtorCarlos())
            .exchange().expectStatus().isNoContent

        val erro = esperarErro(excluir(id), 409)
        assertThat(erro.message).contains("1 produto(s) removido(s)")
    }

    @Test
    fun excluir_ComCliente_Retornar403() {
        esperarErro(excluir(20, Tokens.clienteBia()), 403)
    }
}
