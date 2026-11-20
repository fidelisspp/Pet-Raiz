package com.raizpet.api

import com.raizpet.api.web.dto.PageableDto
import com.raizpet.api.web.dto.PedidoRecebidoResponseDto
import com.raizpet.api.web.dto.PedidoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.util.function.Consumer
import javax.sql.DataSource

/**
 * Pedidos recebidos pelo produtor (/api/v1/pedidos/recebidos).
 * Massa: seed-insert.sql + seed-recebidos.sql (ver o cabeçalho desse arquivo).
 * Carlos (103): 804 (delivered, mai), 802 (pending, abr), 800 (shipped, mar). Davi (106): 803; 801 não pago.
 */
class PedidoRecebidoIT : IntegracaoBase() {

    @Autowired
    lateinit var dataSource: DataSource

    private val davi = Tokens.header(Tokens.criar(106, "PRODUCER", "davi@email.com"))

    // Roda depois do @Sql da IntegracaoBase (seed-insert), que é executado antes de qualquer @BeforeEach
    @BeforeEach
    fun seedRecebidos() {
        ResourceDatabasePopulator(ClassPathResource("sql/seed-recebidos.sql")).apply { setSqlScriptEncoding("UTF-8") }
            .execute(dataSource)
    }

    private fun get(uri: String, quem: Consumer<HttpHeaders>? = Tokens.produtorCarlos()) =
        testClient.get().uri(uri).apply { if (quem != null) headers(quem) }.exchange()

    private fun patch(grupoId: Int, corpo: String, quem: Consumer<HttpHeaders>? = Tokens.produtorCarlos()) =
        testClient.patch().uri("/api/v1/pedidos/recebidos/$grupoId/status")
            .apply { if (quem != null) headers(quem) }
            .contentType(MediaType.APPLICATION_JSON).body(corpo).exchange()

    private fun status(s: String) = """{"status":"$s"}"""

    private fun pagina(uri: String, quem: Consumer<HttpHeaders> = Tokens.produtorCarlos()): PageableDto =
        get(uri, quem).expectStatus().isOk.expectBody(PageableDto::class.java).returnResult().responseBody!!

    @Suppress("UNCHECKED_CAST")
    private fun PageableDto.ids(): List<Int> = content.map { (it as Map<String, Any>)["id"] as Int }

    private fun recebido(grupoId: Int, quem: Consumer<HttpHeaders> = Tokens.produtorCarlos()): PedidoRecebidoResponseDto =
        get("/api/v1/pedidos/recebidos/$grupoId", quem).expectStatus().isOk
            .expectBody(PedidoRecebidoResponseDto::class.java).returnResult().responseBody!!

    private fun avancar(grupoId: Int, s: String): PedidoRecebidoResponseDto =
        patch(grupoId, status(s)).expectStatus().isOk
            .expectBody(PedidoRecebidoResponseDto::class.java).returnResult().responseBody!!

    // ---------- GET /pedidos/recebidos ----------

    @Test
    fun listar_ComTokenDeProdutor_RetornarSoAsPartesDelePagasMaisRecentesPrimeiro() {
        val pagina = pagina("/api/v1/pedidos/recebidos")
        assertThat(pagina.ids()).containsExactly(804, 802, 800)
        assertThat(pagina.totalElements).isEqualTo(3)

        // Não chama nenhum outro serviço
        assertThat(ProdutosStub.consultas.get()).isZero()
        assertThat(ProdutosStub.reservas).isEmpty()
        assertThat(UsuariosStub.chamadas.get()).isZero()
    }

    @Test
    fun listar_FormatoDoItem_TrazSoOsItensETotalDoProdutorComDestinatarioEEndereco() {
        val r = recebido(802)

        assertThat(r.id).isEqualTo(802)
        assertThat(r.orderId).isEqualTo(702)
        assertThat(r.status).isEqualTo("pending")
        assertThat(r.nextStatus).isEqualTo("preparing")
        assertThat(r.shippingMethod).isEqualTo("standard")
        assertThat(r.paidAt).isNotNull()
        assertThat(r.recipientName).isEqualTo("Bia Souza")
        assertThat(r.address.street).isEqualTo("Rua das Flores")
        assertThat(r.address.city).isEqualTo("Atibaia")
        assertThat(r.address.zipCode).isEqualTo("12940-000")
        // Só os itens do Carlos (o mel do Davi, no mesmo pedido, não aparece); total sem frete
        assertThat(r.items.map { it.productId to it.quantity }).containsExactly(100 to 2, 101 to 1)
        assertThat(r.total).isEqualByComparingTo("199.30")
    }

    @Test
    fun listar_ComFiltroDeStatus_RetornarSoOsDoStatus() {
        assertThat(pagina("/api/v1/pedidos/recebidos?status=pending").ids()).containsExactly(802)
        assertThat(pagina("/api/v1/pedidos/recebidos?status=delivered").ids()).containsExactly(804)
        assertThat(pagina("/api/v1/pedidos/recebidos?status=preparing").ids()).isEmpty()
    }

    @Test
    fun listar_ComStatusInvalido_RetornarErrorMessageStatus400() {
        esperarErro(get("/api/v1/pedidos/recebidos?status=cancelado"), 400)
    }

    @Test
    fun listar_ComPaginacao_RetornarPaginaSolicitada() {
        val pagina = pagina("/api/v1/pedidos/recebidos?page=0&size=2")
        assertThat(pagina.ids()).containsExactly(804, 802)
        assertThat(pagina.totalPages).isEqualTo(2)
        assertThat(pagina("/api/v1/pedidos/recebidos?page=1&size=2").ids()).containsExactly(800)
    }

    @Test
    fun listar_PedidoNaoPago_NaoApareceParaOProdutor() {
        // 801 é do Davi, mas o pedido 701 ainda não foi pago
        assertThat(pagina("/api/v1/pedidos/recebidos", davi).ids()).containsExactly(803)
    }

    // ---------- GET /pedidos/recebidos/{grupoId} ----------

    @Test
    fun buscar_DeOutroProdutorNaoPagoOuInexistente_RetornarErrorMessageStatus404() {
        esperarErro(get("/api/v1/pedidos/recebidos/803"), 404)          // do Davi
        esperarErro(get("/api/v1/pedidos/recebidos/801", davi), 404)    // não pago
        esperarErro(get("/api/v1/pedidos/recebidos/9999"), 404)
    }

    // ---------- PATCH /pedidos/recebidos/{grupoId}/status ----------

    @Test
    fun avancar_UmaEtapaPorVez_AteEntregue() {
        assertThat(avancar(802, "preparing").let { it.status to it.nextStatus }).isEqualTo("preparing" to "shipped")
        assertThat(avancar(802, "shipped").let { it.status to it.nextStatus }).isEqualTo("shipped" to "delivered")
        val entregue = avancar(802, "delivered")
        assertThat(entregue.status).isEqualTo("delivered")
        assertThat(entregue.nextStatus).isNull()

        // Gravou de verdade
        assertThat(recebido(802).status).isEqualTo("delivered")
        // Depois de entregue, nada mais
        esperarErro(patch(802, status("delivered")), 409)
    }

    @Test
    fun avancar_PularVoltarOuRepetir_RetornarErrorMessageStatus409() {
        esperarErro(patch(802, status("shipped")), 409)      // pular (pending -> shipped)
        esperarErro(patch(802, status("delivered")), 409)    // pular duas
        esperarErro(patch(802, status("pending")), 409)      // repetir
        esperarErro(patch(800, status("preparing")), 409)    // voltar (shipped -> preparing)
        esperarErro(patch(804, status("pending")), 409)      // voltar do delivered

        assertThat(recebido(802).status).isEqualTo("pending")
        assertThat(recebido(800).status).isEqualTo("shipped")
    }

    @Test
    fun avancar_DeOutroProdutorOuNaoPago_RetornarErrorMessageStatus404() {
        esperarErro(patch(803, status("shipped")), 404)              // Carlos no grupo do Davi
        esperarErro(patch(801, status("preparing"), davi), 404)      // pedido não pago
        assertThat(recebido(803, davi).status).isEqualTo("preparing")
    }

    @Test
    fun avancar_ComCorpoInvalido_RetornarErrorMessageStatus422() {
        esperarErro(patch(802, "{}"), 422)
        esperarErro(patch(802, status("cancelled")), 422)
        esperarErro(patch(802, status("")), 422)
    }

    @Test
    fun avancar_ClienteVeOStatusNovoDaParteNoPedido() {
        avancar(802, "preparing")

        val pedido = testClient.get().uri("/api/v1/pedidos/702").headers(Tokens.clienteBia()).exchange()
            .expectStatus().isOk.expectBody(PedidoResponseDto::class.java).returnResult().responseBody!!
        // A parte do Carlos mudou; a do Davi (mesmo pedido) continua como estava
        assertThat(pedido.producers.associate { it.producerId to it.deliveryStatus })
            .isEqualTo(mapOf(103 to "preparing", 106 to "preparing"))

        avancar(802, "shipped")
        val historico = testClient.get().uri("/api/v1/pedidos").headers(Tokens.clienteBia()).exchange()
            .expectStatus().isOk.expectBody(PageableDto::class.java).returnResult().responseBody!!
        @Suppress("UNCHECKED_CAST")
        val p702 = historico.content.map { it as Map<String, Any?> }.first { it["id"] == 702 }
        @Suppress("UNCHECKED_CAST")
        val partes = (p702["producers"] as List<Map<String, Any?>>).associate { it["producerId"] to it["deliveryStatus"] }
        assertThat(partes).isEqualTo(mapOf(103 to "shipped", 106 to "preparing"))
    }

    // ---------- Segurança ----------

    @Test
    fun rotasRecebidos_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(get("/api/v1/pedidos/recebidos", null), 401)
        esperarErro(get("/api/v1/pedidos/recebidos/802", null), 401)
        esperarErro(patch(802, status("preparing"), null), 401)
    }

    @Test
    fun rotasRecebidos_ComClienteOuAdmin_RetornarErrorMessageStatus403() {
        // Cliente em /pedidos/recebidos cai no controller do produtor (403), e não em GET /pedidos/{id}
        listOf(Tokens.clienteBia(), Tokens.adminAna()).forEach { quem ->
            esperarErro(get("/api/v1/pedidos/recebidos", quem), 403)
            esperarErro(get("/api/v1/pedidos/recebidos/802", quem), 403)
            esperarErro(patch(802, status("preparing"), quem), 403)
        }
        assertThat(recebido(802).status).isEqualTo("pending")
    }

    @Test
    fun rotasDoCliente_ComProdutor_ContinuamRestritasACustomer() {
        esperarErro(get("/api/v1/pedidos/702"), 403)
        esperarErro(get("/api/v1/pedidos"), 403)
    }
}
