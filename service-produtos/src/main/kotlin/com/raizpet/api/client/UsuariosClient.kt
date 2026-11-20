package com.raizpet.api.client

import com.raizpet.api.config.InternoKeyFilter
import com.raizpet.api.exception.ServicoIndisponivelException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** Item de GET /api/v1/interno/produtores/aprovados no service-usuarios. */
data class ProdutorAprovado(
    val id: Int = 0,
    val companyName: String = "",
    val cnpj: String = ""
)

/**
 * Chamada HTTP síncrona ao service-usuarios, com o header X-Internal-Key.
 * Qualquer falha (fora do ar, timeout, 4xx/5xx, JSON inesperado) vira ServicoIndisponivelException,
 * que o ApiExceptionHandler responde como 503 no formato ErrorMessage.
 */
@Component
class UsuariosClient(
    @Value("\${petraiz.usuarios.url}") private val baseUrl: String,
    @Value("\${petraiz.interno.chave}") private val chaveInterna: String,
    @Value("\${petraiz.usuarios.connect-timeout-ms}") connectTimeoutMs: Long,
    @Value("\${petraiz.usuarios.read-timeout-ms}") readTimeoutMs: Long,
    private val jsonMapper: JsonMapper
) {

    companion object {
        const val ROTA_APROVADOS = "/api/v1/interno/produtores/aprovados"
        private val log = LoggerFactory.getLogger(UsuariosClient::class.java)
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        })
        .build()

    /** Produtores aprovados, indexados por id. Uma chamada por requisição ao catálogo. */
    fun buscarProdutoresAprovados(): Map<Int, ProdutorAprovado> {
        val json = try {
            restClient.get()
                .uri(ROTA_APROVADOS)
                .header(InternoKeyFilter.HEADER, chaveInterna)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
        } catch (ex: RestClientException) {
            log.error("service-usuarios indisponível em {}{}: {}", baseUrl, ROTA_APROVADOS, ex.message)
            throw ServicoIndisponivelException()
        }

        val lista = try {
            jsonMapper.readValue(json ?: "", object : TypeReference<List<ProdutorAprovado>>() {})
        } catch (ex: JacksonException) {
            log.error("Resposta inesperada do service-usuarios em {}: {}", ROTA_APROVADOS, ex.message)
            throw ServicoIndisponivelException()
        }
        return lista.associateBy { it.id }
    }
}
