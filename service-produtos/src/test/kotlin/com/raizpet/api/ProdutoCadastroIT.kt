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

/** POST /api/v1/produtos: token validado só pelas claims (id e role), sem banco de usuários. */
class ProdutoCadastroIT : IntegracaoBase() {

    private fun produtoValido() = ProdutoCreateDto(
        name = "Snack de Maçã",
        description = "Maçã desidratada sem açúcar",
        price = BigDecimal("24.90"),
        stock = 15,
        imageUrl = "/images/maca.jpg",
        categoryId = 20
    )

    private fun postProduto(dto: Any, auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()) = testClient
        .post()
        .uri("/api/v1/produtos")
        .apply { if (auth != null) headers(auth) }
        .contentType(MediaType.APPLICATION_JSON)
        .body(dto)
        .exchange()

    @Suppress("UNCHECKED_CAST")
    private fun catalogoIds(query: String): List<Int> =
        testClient.get().uri("/api/v1/produtos$query").exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
            .content.map { (it as Map<String, Any>)["id"] as Int }

    @Test
    fun criarProduto_ComTokenDeProdutor_RetornarProdutoCriadoComDonoDoTokenEStatus201() {
        val responseBody = postProduto(produtoValido())
            .expectStatus().isCreated
            .expectBody(ProdutoCriadoResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.id).isPositive()
        assertThat(responseBody.name).isEqualTo("Snack de Maçã")
        assertThat(responseBody.stock).isEqualTo(15)
        assertThat(responseBody.imageUrl).isEqualTo("/images/maca.jpg")
        assertThat(responseBody.petType).isEqualTo("both")
        assertThat(responseBody.active).isTrue()
        assertThat(responseBody.category.name).isEqualTo("Petiscos")
        // Dono vem da claim id do token
        assertThat(responseBody.producerId).isEqualTo(103)
        // O cadastro não consulta o service-usuarios
        assertThat(UsuariosStub.chamadas.get()).isZero()

        // Já aparece no catálogo público (produtor 103 está aprovado no stub)
        assertThat(catalogoIds("?nome=maçã")).containsExactly(responseBody.id)
    }

    @Test
    fun criarProduto_ComPetTypeInformado_GravarOTipo() {
        val responseBody = postProduto(produtoValido().copy(petType = "cat"))
            .expectStatus().isCreated
            .expectBody(ProdutoCriadoResponseDto::class.java)
            .returnResult().responseBody
        assertThat(responseBody!!.petType).isEqualTo("cat")
        assertThat(catalogoIds("?petType=cat")).contains(responseBody.id)
        assertThat(catalogoIds("?petType=dog")).doesNotContain(responseBody.id)
    }

    @Test
    fun criarProduto_SemImageUrl_GravarImagemPadraoSemFoto() {
        val responseBody = postProduto(produtoValido().copy(imageUrl = null))
            .expectStatus().isCreated
            .expectBody(ProdutoCriadoResponseDto::class.java)
            .returnResult().responseBody
        assertThat(responseBody!!.imageUrl).isEqualTo("/images/sem-foto.png")
    }

    @Test
    fun criarProduto_ComServicoDeUsuariosFora_ContinuaCriando() {
        UsuariosStub.modo = UsuariosStub.Modo.ERRO_500
        postProduto(produtoValido()).expectStatus().isCreated
    }

    @Test
    fun criarProduto_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        listOf(
            produtoValido().copy(name = ""),
            produtoValido().copy(name = "x".repeat(151)),
            produtoValido().copy(price = BigDecimal.ZERO),
            produtoValido().copy(price = BigDecimal("-1.00")),
            produtoValido().copy(price = null),
            produtoValido().copy(price = BigDecimal("10.999")),
            produtoValido().copy(stock = -1),
            produtoValido().copy(stock = null),
            produtoValido().copy(imageUrl = ""),
            produtoValido().copy(imageUrl = "nao-e-caminho"),
            produtoValido().copy(imageUrl = "https://exemplo.com/maca.jpg"),
            produtoValido().copy(imageUrl = "/images/../application.properties"),
            produtoValido().copy(imageUrl = "/images/maca.exe"),
            produtoValido().copy(categoryId = null),
            produtoValido().copy(petType = "peixe")
        ).forEach { esperarErro(postProduto(it), 422) }

        // Estoque zero é permitido
        postProduto(produtoValido().copy(stock = 0)).expectStatus().isCreated
    }

    @Test
    fun criarProduto_ComJsonMalformado_RetornarErrorMessageStatus400() {
        esperarErro(postProduto("{ isto não é json"), 400)
    }

    @Test
    fun criarProduto_ComCategoriaInexistente_RetornarErrorMessageStatus404() {
        esperarErro(postProduto(produtoValido().copy(categoryId = 999)), 404)
    }

    @Test
    fun criarProduto_SemTokenOuComTokenInvalido_RetornarErrorMessageStatus401() {
        esperarErro(postProduto(produtoValido(), auth = null), 401)
        // Expirado
        esperarErro(postProduto(produtoValido(), Tokens.header(Tokens.criar(103, "PRODUCER", expiraEmMs = -60_000))), 401)
        // Assinado com outra chave
        esperarErro(postProduto(produtoValido(), Tokens.header(Tokens.criar(103, "PRODUCER", secret = "outra-chave-outra-chave-outra-chave!"))), 401)
        // Sem a claim id, sem role ou com role desconhecida
        esperarErro(postProduto(produtoValido(), Tokens.header(Tokens.criar(null, "PRODUCER"))), 401)
        esperarErro(postProduto(produtoValido(), Tokens.header(Tokens.criar(103, null))), 401)
        esperarErro(postProduto(produtoValido(), Tokens.header(Tokens.criar(103, "SERVICE"))), 401)
        // Lixo
        esperarErro(postProduto(produtoValido(), Tokens.header("abc.def.ghi")), 401)
    }

    @Test
    fun criarProduto_ComClienteOuAdmin_RetornarErrorMessageStatus403() {
        esperarErro(postProduto(produtoValido(), Tokens.clienteBia()), 403)
        esperarErro(postProduto(produtoValido(), Tokens.adminAna()), 403)
    }

    @Test
    fun criarProduto_ComChaveInternaEmVezDeToken_RetornarErrorMessageStatus401() {
        esperarErro(postProduto(produtoValido(), auth = Consumer { it.add("X-Internal-Key", UsuariosStub.CHAVE) }), 401)
    }
}
