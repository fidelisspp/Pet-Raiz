package com.raizpet.api

import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * service-usuarios realmente fora do ar (conexão recusada): aponta para uma porta sem ninguém.
 * Não estende IntegracaoBase porque lá a URL é trocada pelo stub; por isso sobe um contexto próprio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@TestPropertySource(properties = ["petraiz.usuarios.url=http://localhost:1"])
@Sql(scripts = ["/sql/seed-insert.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, config = SqlConfig(encoding = "UTF-8"))
@Sql(scripts = ["/sql/seed-delete.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UsuariosForaDoArIT {

    @Autowired
    lateinit var testClient: RestTestClient

    private fun esperar503(uri: String) {
        val erro = testClient.get().uri(uri).exchange()
            .expectStatus().isEqualTo(503)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(erro).isNotNull
        assertThat(erro!!.status).isEqualTo(503)
        assertThat(erro.path).isEqualTo(uri.substringBefore("?"))
    }

    @Test
    fun catalogo_ComServicoDeUsuariosDesligado_RetornarErrorMessageStatus503() {
        esperar503("/api/v1/produtos")
        esperar503("/api/v1/produtos?petType=dog")
        esperar503("/api/v1/produtos/100")
    }

    @Test
    fun categoriasCadastroERotasInternas_ComServicoDeUsuariosDesligado_ContinuamFuncionando() {
        testClient.get().uri("/api/v1/categorias").exchange().expectStatus().isOk
        testClient.get().uri("/api/v1/interno/produtos?ids=100")
            .header("X-Internal-Key", UsuariosStub.CHAVE)
            .exchange().expectStatus().isOk
        testClient.post().uri("/api/v1/produtos")
            .headers(Tokens.produtorCarlos())
            .header("Content-Type", "application/json")
            .body("""{"name":"Snack","price":10.00,"stock":1,"imageUrl":"/images/snack.jpg","categoryId":20}""")
            .exchange().expectStatus().isCreated
    }
}
