package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.ProdutosResumoDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Etapa 5.4: produtos publicados na visão do ADMIN.
 * Publicados no seed (ativos de produtor aprovado no UsuariosStub): 100 "Ração Orgânica de Frango",
 * 101 "Petisco de Batata Doce", 102 "Ração Orgânica de Peixe" (estoque 0), todos do 103 "Sítio Raiz Orgânicos",
 * e 106 "Mel para Cães" do 106 "Apiário do Davi". 103 está removido; 104 e 105 são de produtores não aprovados.
 */
class ProdutoAdminIT : IntegracaoBase() {

    @Suppress("UNCHECKED_CAST")
    private fun ids(uri: String): List<Int> =
        testClient.get().uri(uri).headers(Tokens.adminAna())
            .exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
            .content.map { (it as Map<String, Any>)["id"] as Int }

    // ---------- GET /produtos/admin ----------

    @Test
    fun listar_SoPublicadosEmOrdemDeNome() {
        assertThat(ids("/api/v1/produtos/admin")).containsExactly(106, 101, 100, 102)
    }

    @Test
    fun listar_FormatoIgualAoDoCatalogo() {
        val pagina = testClient.get().uri("/api/v1/produtos/admin?busca=mel").headers(Tokens.adminAna())
            .exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
        @Suppress("UNCHECKED_CAST")
        val item = pagina.content[0] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        assertThat((item["producer"] as Map<String, Any?>)["companyName"]).isEqualTo("Apiário do Davi")
        @Suppress("UNCHECKED_CAST")
        assertThat((item["category"] as Map<String, Any?>)["name"]).isEqualTo("Petiscos")
    }

    @Test
    fun listar_BuscaNoNomeDoProdutoOuDoProdutor() {
        assertThat(ids("/api/v1/produtos/admin?busca=RAÇÃO")).containsExactly(100, 102)
        // Nome do produtor (vem do service-usuarios), sem diferenciar maiúscula
        assertThat(ids("/api/v1/produtos/admin?busca=apiário")).containsExactly(106)
        assertThat(ids("/api/v1/produtos/admin?busca=sítio raiz")).containsExactly(101, 100, 102)
        assertThat(ids("/api/v1/produtos/admin?busca=nada-a-ver")).isEmpty()
    }

    @Test
    fun listar_FiltroDeCategoriaCombinadoComBusca() {
        assertThat(ids("/api/v1/produtos/admin?categoriaId=20")).containsExactly(106, 101)
        assertThat(ids("/api/v1/produtos/admin?categoriaId=20&busca=sítio")).containsExactly(101)
    }

    @Test
    fun listar_ComClienteProdutorOuSemToken_Retornar403e401() {
        esperarErro(testClient.get().uri("/api/v1/produtos/admin").headers(Tokens.clienteBia()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/produtos/admin").headers(Tokens.produtorCarlos()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/produtos/admin").exchange(), 401)
    }

    // ---------- GET /produtos/admin/resumo ----------

    @Test
    fun resumo_ContarPublicadosEProdutores() {
        val resumo = testClient.get().uri("/api/v1/produtos/admin/resumo").headers(Tokens.adminAna())
            .exchange().expectStatus().isOk
            .expectBody(ProdutosResumoDto::class.java).returnResult().responseBody!!
        assertThat(resumo).isEqualTo(ProdutosResumoDto(4, 2))
    }

    // ---------- DELETE /produtos/admin/{id} ----------

    @Test
    fun remover_ProdutoDeQualquerProdutor_SumirDoCatalogoEDaLista() {
        testClient.delete().uri("/api/v1/produtos/admin/106").headers(Tokens.adminAna())
            .exchange().expectStatus().isNoContent
        esperarErro(testClient.get().uri("/api/v1/produtos/106").exchange(), 404)
        assertThat(ids("/api/v1/produtos/admin")).doesNotContain(106)
        // De novo: já removido
        esperarErro(testClient.delete().uri("/api/v1/produtos/admin/106").headers(Tokens.adminAna()).exchange(), 404)
    }

    @Test
    fun remover_InexistenteOuJaRemovido_Retornar404() {
        esperarErro(testClient.delete().uri("/api/v1/produtos/admin/9999").headers(Tokens.adminAna()).exchange(), 404)
        esperarErro(testClient.delete().uri("/api/v1/produtos/admin/103").headers(Tokens.adminAna()).exchange(), 404)
    }

    @Test
    fun remover_ComProdutorDono_Retornar403() {
        // O dono remove pela área dele (/produtos/meus); a rota do admin é só do admin
        esperarErro(testClient.delete().uri("/api/v1/produtos/admin/100").headers(Tokens.produtorCarlos()).exchange(), 403)
    }

    // ---------- service-usuarios fora do ar ----------

    @Test
    fun usuariosForaDoAr_ListaEResumo503_RemocaoContinua() {
        UsuariosStub.modo = UsuariosStub.Modo.ERRO_500
        esperarErro(testClient.get().uri("/api/v1/produtos/admin").headers(Tokens.adminAna()).exchange(), 503)
        esperarErro(testClient.get().uri("/api/v1/produtos/admin/resumo").headers(Tokens.adminAna()).exchange(), 503)
        testClient.delete().uri("/api/v1/produtos/admin/100").headers(Tokens.adminAna())
            .exchange().expectStatus().isNoContent
    }
}
