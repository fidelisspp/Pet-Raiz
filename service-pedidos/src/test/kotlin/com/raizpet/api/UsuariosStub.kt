package com.raizpet.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stub HTTP do service-usuarios. Responde GET /api/v1/interno/clientes/{id} como o serviço real:
 * confere o X-Internal-Key (401), 101 = Bia (ativa), 102 = Bob (suspenso), qualquer outro id = 404.
 */
object UsuariosStub {

    enum class Modo { OK, ERRO_500, JSON_INVALIDO }

    private val CLIENTES = mapOf(101 to false, 102 to true)
    private val NOMES = mapOf(101 to "Bia", 102 to "Bob")

    @Volatile
    var modo: Modo = Modo.OK

    val chamadas = AtomicInteger(0)

    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/api/v1/interno/clientes/") { troca ->
            chamadas.incrementAndGet()
            val id = troca.requestURI.path.substringAfterLast("/").toIntOrNull()
            val (status, corpo) = when {
                troca.requestHeaders.getFirst("X-Internal-Key") != ProdutosStub.CHAVE ->
                    401 to """{"status":401,"message":"Rota interna: envie o header X-Internal-Key válido"}"""
                modo == Modo.ERRO_500 -> 500 to """{"status":500}"""
                modo == Modo.JSON_INVALIDO -> 200 to "isto não é json"
                id == null || id !in CLIENTES -> 404 to """{"status":404,"message":"Cliente id=$id não encontrado no sistema"}"""
                else -> 200 to """{"id":$id,"suspended":${CLIENTES.getValue(id)}}"""
            }
            val bytes = corpo.toByteArray(StandardCharsets.UTF_8)
            troca.responseHeaders.add("Content-Type", "application/json")
            troca.sendResponseHeaders(status, bytes.size.toLong())
            troca.responseBody.use { it.write(bytes) }
        }
        // Etapa 5.5: GET /interno/clientes?ids=101,102 ou ?busca=bi -> [{id, name}] (lista de pedidos do admin)
        createContext("/api/v1/interno/clientes") { troca ->
            chamadas.incrementAndGet()
            val parametros = (troca.requestURI.query ?: "").split("&").filter { "=" in it }
                .associate { java.net.URLDecoder.decode(it.substringBefore("="), StandardCharsets.UTF_8) to
                    java.net.URLDecoder.decode(it.substringAfter("="), StandardCharsets.UTF_8) }
            val ids = parametros["ids"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            val busca = parametros["busca"]
            val (status, corpo) = when {
                troca.requestHeaders.getFirst("X-Internal-Key") != ProdutosStub.CHAVE ->
                    401 to """{"status":401,"message":"Rota interna: envie o header X-Internal-Key válido"}"""
                modo == Modo.ERRO_500 -> 500 to """{"status":500}"""
                else -> {
                    val escolhidos = NOMES.filter { (id, nome) ->
                        (ids != null && id in ids) || (busca != null && nome.contains(busca, ignoreCase = true))
                    }
                    200 to escolhidos.entries.joinToString(",", "[", "]") { """{"id":${it.key},"name":"${it.value}"}""" }
                }
            }
            val bytes = corpo.toByteArray(StandardCharsets.UTF_8)
            troca.responseHeaders.add("Content-Type", "application/json")
            troca.sendResponseHeaders(status, bytes.size.toLong())
            troca.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val url: String get() = "http://localhost:${server.address.port}"

    fun reset() {
        modo = Modo.OK
        chamadas.set(0)
    }
}
