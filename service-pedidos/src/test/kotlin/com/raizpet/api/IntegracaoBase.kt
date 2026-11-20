package com.raizpet.api

import com.raizpet.api.web.dto.CarrinhoResponseDto
import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.function.Consumer

/**
 * Base dos testes e2e: banco pet_pedidos_db_test com o seed, e os outros dois serviços trocados
 * pelos stubs HTTP (ProdutosStub e UsuariosStub). As classes que estendem esta (sem configuração
 * extra) compartilham o mesmo contexto do Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
abstract class IntegracaoBase {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun stubs(registry: DynamicPropertyRegistry) {
            registry.add("petraiz.produtos.url") { ProdutosStub.url }
            registry.add("petraiz.usuarios.url") { UsuariosStub.url }
        }
    }

    @Autowired
    lateinit var testClient: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun resetStubs() {
        ProdutosStub.reset()
        UsuariosStub.reset()
    }

    fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int): ErrorMessage {
        val responseBody = spec
            .expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(status)
        return responseBody
    }

    fun post(uri: String, quem: Consumer<HttpHeaders>, corpo: String) =
        testClient.post().uri(uri).headers(quem).contentType(MediaType.APPLICATION_JSON).body(corpo).exchange()

    fun carrinho(quem: Consumer<HttpHeaders>): CarrinhoResponseDto =
        testClient.get().uri("/api/v1/carrinho").headers(quem).exchange()
            .expectStatus().isOk
            .expectBody(CarrinhoResponseDto::class.java).returnResult().responseBody!!

    fun adicionar(quem: Consumer<HttpHeaders>, productId: Int, quantity: Int) =
        post("/api/v1/carrinho/itens", quem, """{"productId":$productId,"quantity":$quantity}""")

    fun contar(tabela: String): Int = jdbc.queryForObject("SELECT COUNT(*) FROM $tabela", Int::class.java)!!
}
