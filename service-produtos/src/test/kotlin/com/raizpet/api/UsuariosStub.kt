package com.raizpet.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stub HTTP do service-usuarios para os testes (JDK HttpServer, sem dependência nova).
 * Responde GET /api/v1/interno/produtores/aprovados como o serviço real: confere o X-Internal-Key
 * (401 se errado) e devolve os aprovados do seed do service-usuarios (103 e 106).
 * Assim o teste passa pelo UsuariosClient de verdade (HTTP, header, JSON, tratamento de erro).
 */
object UsuariosStub {

    enum class Modo { OK, ERRO_500, JSON_INVALIDO, VAZIO }

    // Mesmo valor padrão de petraiz.interno.chave no application.properties
    const val CHAVE = "troque-esta-chave-interna-petraiz"

    private const val APROVADOS = """[
        {"id":103,"companyName":"Sítio Raiz Orgânicos","cnpj":"11.222.333/0001-81"},
        {"id":106,"companyName":"Apiário do Davi","cnpj":"28.641.953/0001-58"}
    ]"""

    @Volatile
    var modo: Modo = Modo.OK

    val chamadas = AtomicInteger(0)

    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/api/v1/interno/produtores/aprovados") { troca ->
            chamadas.incrementAndGet()
            val (status, corpo) = when {
                troca.requestHeaders.getFirst("X-Internal-Key") != CHAVE ->
                    401 to """{"status":401,"message":"Rota interna: envie o header X-Internal-Key válido"}"""
                modo == Modo.ERRO_500 -> 500 to """{"status":500}"""
                modo == Modo.JSON_INVALIDO -> 200 to "isto não é json"
                modo == Modo.VAZIO -> 200 to "[]"
                else -> 200 to APROVADOS
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
