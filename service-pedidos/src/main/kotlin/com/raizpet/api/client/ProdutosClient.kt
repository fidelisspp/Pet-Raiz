package com.raizpet.api.client

import com.raizpet.api.config.InternoKeyFilter
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.ServicoIndisponivelException
import com.raizpet.api.web.exception.ErrorMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Duration

/** Produto como as rotas internas do service-produtos devolvem (ProdutoInternoDto de lá). */
data class ProdutoInterno(
    val id: Int = 0,
    val name: String = "",
    val price: BigDecimal = BigDecimal.ZERO,
    val stock: Int = 0,
    val imageUrl: String = "",
    val active: Boolean = false,
    val producerId: Int = 0
)

/** Item da reserva e do estorno: a MESMA lista nos dois (compensação simplificada, ver README). */
data class ItemEstoque(val productId: Int, val quantity: Int)

private data class EstoqueItens(val itens: List<ItemEstoque>)

/**
 * Chamadas HTTP síncronas ao service-produtos, com o header X-Internal-Key.
 * - Fora do ar, timeout, 5xx, 401 ou JSON inesperado: ServicoIndisponivelException (503).
 * - Reserva recusada (409 estoque/inativo, 404 produto inexistente): ConflitoException (409),
 *   com o errors {productId: motivo} que o service-produtos devolveu.
 */
@Component
class ProdutosClient(
    @Value("\${petraiz.produtos.url}") private val baseUrl: String,
    @Value("\${petraiz.interno.chave}") private val chaveInterna: String,
    @Value("\${petraiz.produtos.connect-timeout-ms}") connectTimeoutMs: Long,
    @Value("\${petraiz.produtos.read-timeout-ms}") readTimeoutMs: Long,
    private val jsonMapper: JsonMapper
) {

    companion object {
        const val ROTA_PRODUTOS = "/api/v1/interno/produtos"
        const val ROTA_RESERVA = "/api/v1/interno/estoque/reserva"
        const val ROTA_ESTORNO = "/api/v1/interno/estoque/estorno"
        private const val INDISPONIVEL = "Serviço de produtos indisponível no momento. Tente novamente em instantes."
        private val log = LoggerFactory.getLogger(ProdutosClient::class.java)
        private val TIPO_LISTA = object : TypeReference<List<ProdutoInterno>>() {}
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        })
        .build()

    /** Produtos que existem (ativos ou não), indexados por id. Id inexistente não vem no mapa. */
    fun buscarPorIds(ids: Collection<Int>): Map<Int, ProdutoInterno> {
        if (ids.isEmpty()) return emptyMap()
        val json = try {
            restClient.get()
                .uri { it.path(ROTA_PRODUTOS).queryParam("ids", ids.distinct().sorted().joinToString(",")).build() }
                .header(InternoKeyFilter.HEADER, chaveInterna)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
        } catch (ex: RestClientException) {
            log.error("service-produtos indisponível em {}{}: {}", baseUrl, ROTA_PRODUTOS, ex.message)
            throw ServicoIndisponivelException(INDISPONIVEL)
        }
        return lerLista(json, ROTA_PRODUTOS).associateBy { it.id }
    }

    /**
     * Baixa o estoque de todos os itens ou de nenhum (transação local do service-produtos).
     * Devolve os produtos com nome, preço, imagem e producerId do momento da reserva.
     *
     * Timeout aqui é o caso que a compensação simplificada NÃO cobre: o service-produtos pode ter
     * baixado o estoque e este serviço não tem como saber. Fica no log para correção manual.
     */
    fun reservar(itens: List<ItemEstoque>): List<ProdutoInterno> {
        val json = try {
            post(ROTA_RESERVA, itens)
        } catch (ex: RestClientResponseException) {
            val status = ex.statusCode.value()
            if (status == HttpStatus.CONFLICT.value() || status == HttpStatus.NOT_FOUND.value()) {
                val erro = lerErro(ex.responseBodyAsString)
                log.warn("Reserva recusada pelo service-produtos ({}): {} {}", status, erro?.message, erro?.errors)
                throw ConflitoException(
                    erro?.message ?: "Reserva de estoque recusada: algum produto não está mais disponível",
                    erro?.errors
                )
            }
            log.error("service-produtos respondeu {} na reserva {}: {}", status, itens, ex.message)
            throw ServicoIndisponivelException(INDISPONIVEL)
        } catch (ex: RestClientException) {
            log.error(
                "Falha na reserva {} (se foi timeout, o estoque PODE ter sido baixado sem pedido; conferir manualmente): {}",
                itens, ex.message
            )
            throw ServicoIndisponivelException(INDISPONIVEL)
        }
        return lerLista(json, ROTA_RESERVA)
    }

    /** Devolve ao estoque a MESMA lista da reserva. Qualquer falha sobe como exceção para quem chamou. */
    fun estornar(itens: List<ItemEstoque>) {
        post(ROTA_ESTORNO, itens)
    }

    private fun post(rota: String, itens: List<ItemEstoque>): String? =
        restClient.post()
            .uri(rota)
            .header(InternoKeyFilter.HEADER, chaveInterna)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(jsonMapper.writeValueAsString(EstoqueItens(itens)))
            .retrieve()
            .body(String::class.java)

    private fun lerLista(json: String?, rota: String): List<ProdutoInterno> =
        try {
            jsonMapper.readValue(json ?: "", TIPO_LISTA)
        } catch (ex: JacksonException) {
            log.error("Resposta inesperada do service-produtos em {}: {}", rota, ex.message)
            throw ServicoIndisponivelException(INDISPONIVEL)
        }

    private fun lerErro(json: String?): ErrorMessage? =
        try {
            json?.let { jsonMapper.readValue(it, ErrorMessage::class.java) }
        } catch (ex: JacksonException) {
            null
        }
}
