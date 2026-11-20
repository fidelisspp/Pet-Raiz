package com.raizpet.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stub HTTP do service-produtos (JDK HttpServer, sem dependência nova). Imita as 3 rotas internas
 * do serviço real, inclusive o "tudo ou nada" da reserva e os formatos de erro (404/409 em ErrorMessage),
 * para o teste passar pelo ProdutosClient de verdade (HTTP, header, JSON, tratamento de erro).
 *
 * Catálogo = seed do service-produtos: 100 (10 un.), 101 (30), 102 (0), 103 (inativo, 5), 106 (5).
 */
object ProdutosStub {

    enum class Modo { OK, ERRO_500, JSON_INVALIDO }

    data class Produto(
        val id: Int,
        var name: String,
        var price: BigDecimal,
        val imageUrl: String,
        var active: Boolean,
        val producerId: Int
    )

    // Mesmo valor padrão de petraiz.interno.chave no application.properties
    const val CHAVE = "troque-esta-chave-interna-petraiz"

    val ESTOQUE_INICIAL = mapOf(100 to 10, 101 to 30, 102 to 0, 103 to 5, 106 to 5)

    private fun catalogoInicial() = listOf(
        Produto(100, "Ração Orgânica de Frango", BigDecimal("89.90"), "/images/frango.jpg", true, 103),
        Produto(101, "Petisco de Batata Doce", BigDecimal("19.50"), "/images/batata.jpg", true, 103),
        Produto(102, "Ração Orgânica de Peixe", BigDecimal("99.00"), "/images/peixe.jpg", true, 103),
        Produto(103, "Biscoito Descontinuado", BigDecimal("15.00"), "/images/biscoito.jpg", false, 103),
        Produto(106, "Mel para Cães", BigDecimal("30.00"), "/images/mel.jpg", true, 106)
    ).associateBy { it.id }

    private val json = JsonMapper.builder().build()

    @Volatile var modo: Modo = Modo.OK                // consulta e reserva
    @Volatile var modoEstorno: Modo = Modo.OK

    val produtos = ConcurrentHashMap<Int, Produto>()
    val estoque = ConcurrentHashMap<Int, Int>()

    val consultas = AtomicInteger(0)
    // Corpos (já lidos como productId -> quantity, na ordem enviada) das reservas e estornos recebidos
    val reservas = CopyOnWriteArrayList<List<Pair<Int, Int>>>()
    val estornos = CopyOnWriteArrayList<List<Pair<Int, Int>>>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/api/v1/interno/produtos") { troca ->
            protegido(troca) {
                consultas.incrementAndGet()
                if (modo != Modo.OK) return@protegido falha(modo)
                val ids = troca.requestURI.query.orEmpty().substringAfter("ids=").split(",").mapNotNull { it.trim().toIntOrNull() }
                200 to lista(ids.distinct().sorted().mapNotNull { produtos[it] })
            }
        }
        createContext("/api/v1/interno/estoque/reserva") { troca ->
            protegido(troca) {
                val itens = lerItens(troca)
                reservas.add(itens)
                if (modo != Modo.OK) return@protegido falha(modo)
                reservar(somar(itens), troca.requestURI.path)
            }
        }
        createContext("/api/v1/interno/estoque/estorno") { troca ->
            protegido(troca) {
                val itens = lerItens(troca)
                estornos.add(itens)
                if (modoEstorno != Modo.OK) return@protegido falha(modoEstorno)
                val qtd = somar(itens)
                qtd.forEach { (id, q) -> estoque.merge(id, q, Int::plus) }
                200 to lista(qtd.keys.sorted().mapNotNull { produtos[it] })
            }
        }
        start()
    }

    val url: String get() = "http://localhost:${server.address.port}"

    fun reset() {
        modo = Modo.OK
        modoEstorno = Modo.OK
        produtos.clear()
        produtos.putAll(catalogoInicial())
        estoque.clear()
        estoque.putAll(ESTOQUE_INICIAL)
        consultas.set(0)
        reservas.clear()
        estornos.clear()
    }

    init {
        reset()
    }

    @Synchronized
    private fun reservar(qtd: Map<Int, Int>, path: String): Pair<Int, String> {
        val faltando = qtd.keys.filter { !produtos.containsKey(it) }
        if (faltando.isNotEmpty()) {
            return 404 to erro(path, 404, "Produto(s) não encontrado(s): ${faltando.sorted().joinToString(", ")}", null)
        }
        val falhas = linkedMapOf<String, String>()
        qtd.forEach { (id, q) ->
            val p = produtos.getValue(id)
            val disponivel = estoque.getValue(id)
            when {
                !p.active -> falhas["$id"] = "produto inativo"
                disponivel < q -> falhas["$id"] = "estoque insuficiente: pedido $q, disponível $disponivel"
            }
        }
        if (falhas.isNotEmpty()) {
            return 409 to erro(
                path, 409,
                "Reserva recusada: estoque insuficiente ou produto indisponível. Nenhum item foi baixado.", falhas
            )
        }
        qtd.forEach { (id, q) -> estoque.merge(id, -q, Int::plus) }
        return 200 to lista(qtd.keys.sorted().map { produtos.getValue(it) })
    }

    private fun somar(itens: List<Pair<Int, Int>>): Map<Int, Int> =
        itens.groupingBy { it.first }.fold(0) { s, i -> s + i.second }

    private fun lerItens(troca: HttpExchange): List<Pair<Int, Int>> {
        val corpo = troca.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
        val itens: Iterable<tools.jackson.databind.JsonNode> = json.readTree(corpo)["itens"]
        return itens.map { it["productId"].asInt() to it["quantity"].asInt() }
    }

    private fun lista(ps: List<Produto>): String = json.writeValueAsString(ps.map {
        mapOf(
            "id" to it.id, "name" to it.name, "price" to it.price, "stock" to estoque.getValue(it.id),
            "imageUrl" to it.imageUrl, "active" to it.active, "producerId" to it.producerId
        )
    })

    private fun erro(path: String, status: Int, mensagem: String, errors: Map<String, String>?): String =
        json.writeValueAsString(buildMap {
            put("path", path); put("method", "POST"); put("status", status); put("message", mensagem)
            if (errors != null) put("errors", errors)
        })

    private fun falha(m: Modo): Pair<Int, String> =
        if (m == Modo.JSON_INVALIDO) 200 to "isto não é json" else 500 to """{"status":500}"""

    private fun protegido(troca: HttpExchange, acao: () -> Pair<Int, String>) {
        val (status, corpo) =
            if (troca.requestHeaders.getFirst("X-Internal-Key") != CHAVE)
                401 to """{"status":401,"message":"Rota interna: envie o header X-Internal-Key válido"}"""
            else acao()
        val bytes = corpo.toByteArray(StandardCharsets.UTF_8)
        troca.responseHeaders.add("Content-Type", "application/json")
        troca.sendResponseHeaders(status, bytes.size.toLong())
        troca.responseBody.use { it.write(bytes) }
    }
}
