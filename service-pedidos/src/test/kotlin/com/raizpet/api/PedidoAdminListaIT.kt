package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.PedidoAdminDto
import com.raizpet.api.web.dto.PedidosResumoDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * Etapa 5.5: lista, resumo, cancelamento e correção de status pelo ADMIN.
 * Seed: 700 do Bob (102), PAGO em 2026-03-01, parte 800 do produtor 103 já ENVIADA (produto 100 x1);
 *       701 da Bia (101), pagamento PENDENTE, parte 801 do produtor 106 NOVA (produto 106 x2).
 */
class PedidoAdminListaIT : IntegracaoBase() {

    @Suppress("UNCHECKED_CAST")
    private fun lista(query: String = ""): List<Map<String, Any?>> =
        testClient.get().uri("/api/v1/pedidos/admin$query").headers(Tokens.adminAna())
            .exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
            .content.map { it as Map<String, Any?> }

    private fun ids(query: String = "") = lista(query).map { it["id"] as Int }

    private fun corrigir(grupo: Int, status: String) = testClient.patch()
        .uri("/api/v1/pedidos/admin/grupos/$grupo/status").headers(Tokens.adminAna())
        .contentType(MediaType.APPLICATION_JSON).body("""{"status":"$status"}""").exchange()

    private fun cancelar(id: Int) = testClient.post()
        .uri("/api/v1/pedidos/admin/$id/cancelamento").headers(Tokens.adminAna()).exchange()

    private fun resumo(): PedidosResumoDto = testClient.get().uri("/api/v1/pedidos/admin/resumo").headers(Tokens.adminAna())
        .exchange().expectStatus().isOk.expectBody(PedidosResumoDto::class.java).returnResult().responseBody!!

    // ---------- Lista ----------

    @Test
    fun listar_TodosOsPedidosMaisRecentesPrimeiroComNomeDoCliente() {
        val pedidos = lista()
        assertThat(pedidos.map { it["id"] }).containsExactly(700, 701)
        assertThat(pedidos.map { it["customerName"] }).containsExactly("Bob", "Bia")

        val bob = pedidos[0]
        assertThat(bob["paymentStatus"]).isEqualTo("paid")
        assertThat(bob["cancelable"]).isEqualTo(false)
        @Suppress("UNCHECKED_CAST")
        val parte = (bob["producers"] as List<Map<String, Any?>>)[0]
        assertThat(parte["groupId"]).isEqualTo(800)
        assertThat(parte["deliveryStatus"]).isEqualTo("shipped")
        assertThat(parte["itemCount"]).isEqualTo(1)

        val bia = pedidos[1]
        assertThat(bia["paymentStatus"]).isEqualTo("pending")
        assertThat(bia["cancelable"]).isEqualTo(true)
    }

    @Test
    fun listar_BuscaPorNumeroOuNomeDoCliente() {
        // "#700": a URL é montada pelo builder, que codifica o # uma vez só
        @Suppress("UNCHECKED_CAST")
        val comCerquilha = testClient.get().uri { it.path("/api/v1/pedidos/admin").queryParam("busca", "#700").build() }
            .headers(Tokens.adminAna()).exchange().expectStatus().isOk
            .expectBody(PageableDto::class.java).returnResult().responseBody!!
            .content.map { (it as Map<String, Any?>)["id"] }
        assertThat(comCerquilha).containsExactly(700)
        assertThat(ids("?busca=701")).containsExactly(701)
        assertThat(ids("?busca=bi")).containsExactly(701)
        assertThat(ids("?busca=ninguem")).isEmpty()
    }

    @Test
    fun listar_Filtros() {
        assertThat(ids("?status=enviados")).containsExactly(700)
        assertThat(ids("?status=pendentes")).containsExactly(701)
        assertThat(ids("?status=entregues")).isEmpty()
        assertThat(ids("?status=atrasados")).isEmpty()
        assertThat(ids("?status=cancelados")).isEmpty()
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin?status=xyz").headers(Tokens.adminAna()).exchange(), 400)
    }

    @Test
    fun listar_UsuariosForaDoAr_ListaSemNomes() {
        UsuariosStub.modo = UsuariosStub.Modo.ERRO_500
        assertThat(lista().map { it["customerName"] }).containsOnlyNulls()
    }

    @Test
    fun listar_ComClienteOuSemToken_Retornar403e401() {
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin").headers(Tokens.clienteBia()).exchange(), 403)
        esperarErro(testClient.get().uri("/api/v1/pedidos/admin").exchange(), 401)
    }

    // ---------- Correção de status e atraso ----------

    @Test
    fun corrigir_VoltarParaPendente_PedidoPagoHaMaisDe3DiasFicaAtrasado() {
        val dto = corrigir(800, "pending").expectStatus().isOk
            .expectBody(PedidoAdminDto::class.java).returnResult().responseBody!!
        assertThat(dto.producers[0].deliveryStatus).isEqualTo("pending")
        assertThat(dto.late).isTrue
        assertThat(dto.daysLate).isGreaterThan(3)
        assertThat(dto.cancelable).isTrue
        assertThat(dto.customerName).isEqualTo("Bob")

        assertThat(ids("?status=atrasados")).containsExactly(700)
        assertThat(resumo().late).isEqualTo(1)
    }

    @Test
    fun corrigir_PedidoNaoPagoOuStatusInvalidoOuInexistente_Retornar409_422_404() {
        assertThat(esperarErro(corrigir(801, "preparing"), 409).message).contains("ainda não foi pago")
        assertThat(esperarErro(corrigir(800, "canceled"), 422).errors).containsKey("status")
        esperarErro(corrigir(999, "preparing"), 404)
    }

    // ---------- Cancelamento ----------

    @Test
    fun cancelar_PedidoNaoPago_CancelaTudoEDevolveOEstoque() {
        val dto = cancelar(701).expectStatus().isOk
            .expectBody(PedidoAdminDto::class.java).returnResult().responseBody!!
        assertThat(dto.canceledAt).isNotNull
        assertThat(dto.producers.map { it.deliveryStatus }).containsOnly("canceled")
        // Não estava pago: não há o que estornar no pagamento
        assertThat(dto.paymentStatus).isEqualTo("pending")
        assertThat(ProdutosStub.estornos).containsExactly(listOf(106 to 2))

        assertThat(ids("?status=cancelados")).containsExactly(701)
        assertThat(esperarErro(cancelar(701), 409).message).contains("já foi cancelado")
        assertThat(esperarErro(corrigir(801, "preparing"), 409).message).contains("cancelado")
    }

    @Test
    fun cancelar_ComParteJaEnviada_Retornar409SemMexerNoEstoque() {
        assertThat(esperarErro(cancelar(700), 409).message).contains("já enviaram")
        assertThat(ProdutosStub.estornos).isEmpty()
    }

    @Test
    fun cancelar_PedidoPago_PagamentoViraRefundedEProdutorVeCancelado() {
        corrigir(800, "preparing").expectStatus().isOk
        val dto = cancelar(700).expectStatus().isOk
            .expectBody(PedidoAdminDto::class.java).returnResult().responseBody!!
        assertThat(dto.paymentStatus).isEqualTo("refunded")
        assertThat(dto.late).isFalse

        // O produtor continua vendo a parte dele, agora cancelada e sem próximo status
        testClient.get().uri("/api/v1/pedidos/recebidos/800").headers(Tokens.produtorCarlos())
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("canceled")
            .jsonPath("$.nextStatus").doesNotExist()
        val erro = esperarErro(
            testClient.patch().uri("/api/v1/pedidos/recebidos/800/status").headers(Tokens.produtorCarlos())
                .contentType(MediaType.APPLICATION_JSON).body("""{"status":"shipped"}""").exchange(),
            409
        )
        assertThat(erro.message).contains("cancelado pelo administrador")

        // O cliente vê a parte cancelada no pedido dele
        testClient.get().uri("/api/v1/pedidos/700").headers(Tokens.clienteBobSuspenso())
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.producers[0].deliveryStatus").isEqualTo("canceled")
    }

    @Test
    fun cancelar_EstornoFalha_Retornar503ENadaMuda() {
        ProdutosStub.modoEstorno = ProdutosStub.Modo.ERRO_500
        esperarErro(cancelar(701), 503)
        assertThat(ids("?status=cancelados")).isEmpty()
        assertThat(ids("?status=pendentes")).containsExactly(701)
    }

    // ---------- Resumo ----------

    @Test
    fun resumo_PedidosDoSeedSaoAntigos() {
        assertThat(resumo()).isEqualTo(PedidosResumoDto(0, 0))
    }
}
