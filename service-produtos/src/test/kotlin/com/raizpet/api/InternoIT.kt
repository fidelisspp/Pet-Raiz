package com.raizpet.api

import com.raizpet.api.web.dto.EstoqueItensDto
import com.raizpet.api.web.dto.ItemEstoqueDto
import com.raizpet.api.web.dto.ProdutoInternoDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType

/**
 * Rotas internas (X-Internal-Key), usadas pelo service-pedidos.
 * Estoques do seed: 100=10, 101=30, 102=0, 103=5 (inativo), 106=5.
 */
class InternoIT : IntegracaoBase() {

    private val tipoLista = object : ParameterizedTypeReference<List<ProdutoInternoDto>>() {}

    private fun itens(vararg pares: Pair<Int?, Int?>) =
        EstoqueItensDto(pares.map { ItemEstoqueDto(it.first, it.second) })

    private fun post(rota: String, corpo: Any, chave: String? = UsuariosStub.CHAVE) = testClient
        .post()
        .uri("/api/v1/interno/estoque/$rota")
        .apply { if (chave != null) header("X-Internal-Key", chave) }
        .contentType(MediaType.APPLICATION_JSON)
        .body(corpo)
        .exchange()

    private fun produtos(ids: String): List<ProdutoInternoDto> =
        testClient.get().uri("/api/v1/interno/produtos?ids=$ids")
            .header("X-Internal-Key", UsuariosStub.CHAVE)
            .exchange()
            .expectStatus().isOk
            .expectBody(tipoLista).returnResult().responseBody!!

    private fun estoques(): Map<Int, Int> = produtos("100,101,102,103,106").associate { it.id to it.stock }

    private val estoqueInicial = mapOf(100 to 10, 101 to 30, 102 to 0, 103 to 5, 106 to 5)

    // ---------- proteção ----------

    @Test
    fun rotasInternas_SemChaveComChaveErradaOuSoComTokenDeUsuario_RetornarErrorMessageStatus401() {
        esperarErro(testClient.get().uri("/api/v1/interno/produtos?ids=100").exchange(), 401)
        esperarErro(testClient.get().uri("/api/v1/interno/produtos?ids=100").header("X-Internal-Key", "errada").exchange(), 401)
        esperarErro(testClient.get().uri("/api/v1/interno/produtos?ids=100").headers(Tokens.adminAna()).exchange(), 401)
        esperarErro(post("reserva", itens(100 to 1), chave = null), 401)
        esperarErro(post("estorno", itens(100 to 1), chave = "errada"), 401)
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }

    // ---------- GET /interno/produtos?ids= ----------

    @Test
    fun buscarPorIds_RetornarOsQueExistemInclusiveInativosOrdenadosPorId() {
        val lista = produtos("106, 103,100,999,100")
        assertThat(lista.map { it.id }).containsExactly(100, 103, 106)

        val p100 = lista.first { it.id == 100 }
        assertThat(p100.name).isEqualTo("Ração Orgânica de Frango")
        assertThat(p100.price).isEqualByComparingTo("89.90")
        assertThat(p100.stock).isEqualTo(10)
        assertThat(p100.producerId).isEqualTo(103)
        assertThat(p100.imageUrl).isEqualTo("/images/frango.jpg")
        assertThat(p100.active).isTrue()
        assertThat(lista.first { it.id == 103 }.active).isFalse()

        // Não depende do service-usuarios
        assertThat(UsuariosStub.chamadas.get()).isZero()
    }

    @Test
    fun buscarPorIds_ComIdsInvalidos_RetornarErrorMessageStatus400() {
        listOf("", "?ids=", "?ids=abc", "?ids=1,x", "?ids=" + (1..101).joinToString(",")).forEach { q ->
            esperarErro(
                testClient.get().uri("/api/v1/interno/produtos$q").header("X-Internal-Key", UsuariosStub.CHAVE).exchange(),
                400
            )
        }
    }

    // ---------- POST /interno/estoque/reserva ----------

    @Test
    fun reservar_ComEstoqueSuficiente_BaixarTodosOsItensERetornarStatus200() {
        val lista = post("reserva", itens(100 to 3, 101 to 30, 106 to 1))
            .expectStatus().isOk
            .expectBody(tipoLista).returnResult().responseBody!!

        assertThat(lista.associate { it.id to it.stock }).isEqualTo(mapOf(100 to 7, 101 to 0, 106 to 4))
        assertThat(estoques()).isEqualTo(mapOf(100 to 7, 101 to 0, 102 to 0, 103 to 5, 106 to 4))
    }

    @Test
    fun reservar_ComUmItemSemEstoque_RetornarErrorMessageStatus409ENaoBaixarNada() {
        val erro = esperarErro(post("reserva", itens(100 to 2, 101 to 31, 102 to 1)), 409)

        assertThat(erro.errors).containsOnlyKeys("101", "102")
        assertThat(erro.errors!!["101"]).contains("disponível 30")
        assertThat(erro.message).contains("Nenhum item foi baixado")
        // 100 tinha estoque, mas também não foi baixado: tudo ou nada
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }

    @Test
    fun reservar_ComProdutoRepetido_SomarAsQuantidades() {
        // 6 + 5 = 11 > 10
        esperarErro(post("reserva", itens(100 to 6, 100 to 5)), 409)
        assertThat(estoques()).isEqualTo(estoqueInicial)

        post("reserva", itens(100 to 6, 100 to 4)).expectStatus().isOk
        assertThat(estoques()[100]).isEqualTo(0)
    }

    @Test
    fun reservar_ComProdutoInativo_RetornarErrorMessageStatus409() {
        val erro = esperarErro(post("reserva", itens(103 to 1)), 409)
        assertThat(erro.errors!!["103"]).isEqualTo("produto inativo")
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }

    @Test
    fun reservar_ComProdutoInexistente_RetornarErrorMessageStatus404ENaoBaixarNada() {
        val erro = esperarErro(post("reserva", itens(100 to 1, 999 to 1)), 404)
        assertThat(erro.message).contains("999")
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }

    @Test
    fun reservarEEstornar_ComDadosInvalidos_RetornarErrorMessageStatus422() {
        listOf("reserva", "estorno").forEach { rota ->
            listOf(
                EstoqueItensDto(emptyList()),
                EstoqueItensDto(null),
                itens(null to 1),
                itens(100 to null),
                itens(100 to 0),
                itens(100 to -2)
            ).forEach { esperarErro(post(rota, it), 422) }
        }
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }

    // ---------- POST /interno/estoque/estorno ----------

    @Test
    fun estornar_ComAMesmaListaDaReserva_DevolverOEstoque() {
        val pedido = itens(100 to 3, 101 to 30, 106 to 5)
        post("reserva", pedido).expectStatus().isOk
        assertThat(estoques()).isEqualTo(mapOf(100 to 7, 101 to 0, 102 to 0, 103 to 5, 106 to 0))

        val lista = post("estorno", pedido)
            .expectStatus().isOk
            .expectBody(tipoLista).returnResult().responseBody!!
        assertThat(lista.associate { it.id to it.stock }).isEqualTo(mapOf(100 to 10, 101 to 30, 106 to 5))
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }

    @Test
    fun estornar_SemIdempotencia_DevolveDuasVezesSeReenviado() {
        // Comportamento aceito na compensação simplificada (service-pedidos/README.md)
        post("estorno", itens(102 to 2)).expectStatus().isOk
        post("estorno", itens(102 to 2)).expectStatus().isOk
        assertThat(estoques()[102]).isEqualTo(4)
    }

    @Test
    fun estornar_ComProdutoInexistente_RetornarErrorMessageStatus404ENaoDevolverNada() {
        esperarErro(post("estorno", itens(100 to 1, 999 to 1)), 404)
        assertThat(estoques()).isEqualTo(estoqueInicial)
    }
}
