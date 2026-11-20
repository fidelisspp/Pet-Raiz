package com.raizpet.api.client

import com.raizpet.api.config.InternoKeyFilter
import com.raizpet.api.exception.ServicoIndisponivelException
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
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** Resposta de GET /api/v1/interno/clientes/{id} no service-usuarios. */
data class ClienteInterno(
    val id: Int = 0,
    val suspended: Boolean = false
)

/**
 * Chamada HTTP síncrona ao service-usuarios, com o header X-Internal-Key.
 * 404 de lá (id não é de cliente) vira null; qualquer outra falha vira ServicoIndisponivelException (503).
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
        const val ROTA_CLIENTE = "/api/v1/interno/clientes/{id}"
        private const val INDISPONIVEL = "Serviço de usuários indisponível no momento. Tente novamente em instantes."
        private val log = LoggerFactory.getLogger(UsuariosClient::class.java)
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        })
        .build()

    fun buscarCliente(id: Int): ClienteInterno? {
        val json = try {
            restClient.get()
                .uri(ROTA_CLIENTE, id)
                .header(InternoKeyFilter.HEADER, chaveInterna)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.value() == HttpStatus.NOT_FOUND.value()) return null
            log.error("service-usuarios respondeu {} em {}{} (id={}): {}", ex.statusCode, baseUrl, ROTA_CLIENTE, id, ex.message)
            throw ServicoIndisponivelException(INDISPONIVEL)
        } catch (ex: RestClientException) {
            log.error("service-usuarios indisponível em {}{} (id={}): {}", baseUrl, ROTA_CLIENTE, id, ex.message)
            throw ServicoIndisponivelException(INDISPONIVEL)
        }

        val cliente = try {
            jsonMapper.readValue(json ?: "", ClienteInterno::class.java)
        } catch (ex: JacksonException) {
            log.error("Resposta inesperada do service-usuarios em {}: {}", ROTA_CLIENTE, ex.message)
            throw ServicoIndisponivelException(INDISPONIVEL)
        }
        if (cliente.id != id) {
            log.error("service-usuarios devolveu o cliente {} quando foi pedido o {}", cliente.id, id)
            throw ServicoIndisponivelException(INDISPONIVEL)
        }
        return cliente
    }
}
