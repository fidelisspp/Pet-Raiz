package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * GET /api/v1/produtos e /{id}. Seed: visíveis 100, 101, 102 (produtor 103) e 106 (produtor 106).
 * 103 é inativo; 104 e 105 são de produtores que o stub não devolve como aprovados.
 */
class ProdutoCatalogoIT : IntegracaoBase() {

    private fun catalogo(query: String = ""): PageableDto {
        val responseBody = testClient
            .get()
            .uri("/api/v1/produtos$query")
            .exchange()
            .expectStatus().isOk
            .expectBody(PageableDto::class.java)
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        return responseBody!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun PageableDto.ids(): List<Int> = content.map { (it as Map<String, Any>)["id"] as Int }

    // ---------- GET /produtos ----------

    @Test
    fun listarCatalogo_SemToken_RetornarSoAtivosDeProdutorAprovadoComStatus200() {
        val page = catalogo()

        assertThat(page.ids()).containsExactlyInAnyOrder(100, 101, 102, 106)
        assertThat(page.totalElements).isEqualTo(4)
        assertThat(page.ids()).doesNotContain(103, 104, 105)
    }

    @Test
    fun listarCatalogo_FazUmaUnicaChamadaAoServicoDeUsuariosPorRequisicao() {
        catalogo()
        assertThat(UsuariosStub.chamadas.get()).isEqualTo(1)
        catalogo("?nome=ração&size=1&page=1")
        assertThat(UsuariosStub.chamadas.get()).isEqualTo(2)
    }

    @Test
    fun listarCatalogo_FormatoDoItem_ExpoeSoDadosPublicosDoProdutorECaminhoRelativoDaImagem() {
        val json = testClient
            .get()
            .uri("/api/v1/produtos?nome=frango")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult().responseBody

        assertThat(json)
            .contains("\"imageUrl\":\"/images/frango.jpg\"")
            .contains("\"petType\":\"dog\"")
            .contains("\"category\":{\"id\":10,\"name\":\"Ração Natural\"}")
            .contains("\"producer\":{\"id\":103,\"companyName\":\"Sítio Raiz Orgânicos\",\"cnpj\":\"11.222.333/0001-81\",\"verified\":true}")
            .doesNotContain("producerId")
            .doesNotContain("password")
    }

    @Test
    fun listarCatalogo_ComFiltroNome_RetornarSoOsQueContemONomeSemDiferenciarMaiusculas() {
        assertThat(catalogo("?nome=ORGÂNICA").ids()).containsExactlyInAnyOrder(100, 102)
        assertThat(catalogo("?nome=batata").ids()).containsExactly(101)
        assertThat(catalogo("?nome=Descontinuado").ids()).isEmpty()
        assertThat(catalogo("?nome=Pendente").ids()).isEmpty()
        assertThat(catalogo("?nome=   ").ids()).hasSize(4)
    }

    @Test
    fun listarCatalogo_ComFiltroCategoria_RetornarSoDaCategoria() {
        assertThat(catalogo("?categoriaId=10").ids()).containsExactlyInAnyOrder(100, 102)
        assertThat(catalogo("?categoriaId=20").ids()).containsExactlyInAnyOrder(101, 106)
        assertThat(catalogo("?categoriaId=999").ids()).isEmpty()
        assertThat(catalogo("?categoriaId=10&nome=peixe").ids()).containsExactly(102)
    }

    @Test
    fun listarCatalogo_ComFiltroProdutor_RetornarSoDoProdutorAprovado() {
        assertThat(catalogo("?produtorId=103").ids()).containsExactlyInAnyOrder(100, 101, 102)
        assertThat(catalogo("?produtorId=106").ids()).containsExactly(106)
        // Pendente, rejeitado ou inexistente: página vazia (não 404)
        assertThat(catalogo("?produtorId=104").ids()).isEmpty()
        assertThat(catalogo("?produtorId=105").totalElements).isZero()
        assertThat(catalogo("?produtorId=999").ids()).isEmpty()
        assertThat(catalogo("?produtorId=103&categoriaId=20").ids()).containsExactly(101)
    }

    @Test
    fun listarCatalogo_ComFiltroPetType_IncluiOsProdutosParaAmbos() {
        // 100 dog, 101 both, 102 cat, 106 dog
        assertThat(catalogo("?petType=dog").ids()).containsExactlyInAnyOrder(100, 101, 106)
        assertThat(catalogo("?petType=cat").ids()).containsExactlyInAnyOrder(101, 102)
        assertThat(catalogo("?petType=both").ids()).containsExactly(101)
        assertThat(catalogo("?petType=CAT").ids()).containsExactlyInAnyOrder(101, 102)
        assertThat(catalogo("?petType=dog&produtorId=106").ids()).containsExactly(106)
    }

    @Test
    fun listarCatalogo_ComPetTypeOuCategoriaInvalidos_RetornarErrorMessageStatus400() {
        esperarErro(testClient.get().uri("/api/v1/produtos?petType=peixe").exchange(), 400)
        esperarErro(testClient.get().uri("/api/v1/produtos?categoriaId=abc").exchange(), 400)
    }

    @Test
    fun listarCatalogo_ComPaginacao_RetornarPaginaSolicitada() {
        var page = catalogo("?size=3&page=0")
        assertThat(page.content).hasSize(3)
        assertThat(page.number).isEqualTo(0)
        assertThat(page.size).isEqualTo(3)
        assertThat(page.totalElements).isEqualTo(4)
        assertThat(page.totalPages).isEqualTo(2)
        assertThat(page.first).isTrue()
        assertThat(page.last).isFalse()

        page = catalogo("?size=3&page=1")
        assertThat(page.content).hasSize(1)
        assertThat(page.number).isEqualTo(1)
        assertThat(page.last).isTrue()

        // Ordenação padrão por nome e ordenação informada
        assertThat(catalogo().ids()).containsExactly(106, 101, 100, 102)
        assertThat(catalogo("?sort=price,desc").ids()).containsExactly(102, 100, 106, 101)
    }

    @Test
    fun listarCatalogo_SemNenhumProdutorAprovado_RetornarPaginaVazia() {
        UsuariosStub.modo = UsuariosStub.Modo.VAZIO
        val page = catalogo()
        assertThat(page.content).isEmpty()
        assertThat(page.totalElements).isZero()
    }

    // ---------- service-usuarios com problema -> 503 ----------

    @Test
    fun catalogo_ComServicoDeUsuariosRespondendoErro_RetornarErrorMessageStatus503() {
        UsuariosStub.modo = UsuariosStub.Modo.ERRO_500
        val erro = esperarErro(testClient.get().uri("/api/v1/produtos").exchange(), 503)
        assertThat(erro.path).isEqualTo("/api/v1/produtos")
        assertThat(erro.method).isEqualTo("GET")
        assertThat(erro.statusText).isEqualTo("Service Unavailable")
        assertThat(erro.message).contains("indisponível")

        esperarErro(testClient.get().uri("/api/v1/produtos/100").exchange(), 503)
    }

    @Test
    fun catalogo_ComRespostaInvalidaDoServicoDeUsuarios_RetornarErrorMessageStatus503() {
        UsuariosStub.modo = UsuariosStub.Modo.JSON_INVALIDO
        esperarErro(testClient.get().uri("/api/v1/produtos").exchange(), 503)
    }

    // ---------- GET /produtos/{id} ----------

    @Test
    fun buscarProduto_ComIdDoCatalogo_RetornarProdutoComStatus200() {
        val responseBody = testClient
            .get()
            .uri("/api/v1/produtos/100")
            .exchange()
            .expectStatus().isOk
            .expectBody(ProdutoResponseDto::class.java)
            .returnResult().responseBody

        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.name).isEqualTo("Ração Orgânica de Frango")
        assertThat(responseBody.price).isEqualByComparingTo("89.90")
        assertThat(responseBody.petType).isEqualTo("dog")
        assertThat(responseBody.imageUrl).isEqualTo("/images/frango.jpg")
        assertThat(responseBody.category.id).isEqualTo(10)
        assertThat(responseBody.producer.id).isEqualTo(103)
        assertThat(responseBody.producer.companyName).isEqualTo("Sítio Raiz Orgânicos")
        assertThat(responseBody.producer.verified).isTrue()
        assertThat(UsuariosStub.chamadas.get()).isEqualTo(1)
    }

    @Test
    fun buscarProduto_InexistenteInativoOuDeProdutorNaoAprovado_RetornarErrorMessageStatus404() {
        listOf(0, 103, 104, 105, 9999).forEach { id ->
            esperarErro(testClient.get().uri("/api/v1/produtos/$id").exchange(), 404)
        }
    }
}
