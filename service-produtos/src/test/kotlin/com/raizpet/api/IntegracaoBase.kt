package com.raizpet.api

import com.raizpet.api.web.exception.ErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Base dos testes e2e: banco pet_produtos_db_test com o seed, e o service-usuarios trocado pelo
 * UsuariosStub. Todas as classes que estendem esta compartilham o mesmo contexto do Spring.
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
        fun usuariosStub(registry: DynamicPropertyRegistry) {
            registry.add("petraiz.usuarios.url") { UsuariosStub.url }
        }
    }

    @Autowired
    lateinit var testClient: RestTestClient

    @BeforeEach
    fun resetStub() = UsuariosStub.reset()

    fun esperarErro(spec: RestTestClient.ResponseSpec, status: Int): ErrorMessage {
        val responseBody = spec
            .expectStatus().isEqualTo(status)
            .expectBody(ErrorMessage::class.java)
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        assertThat(responseBody!!.status).isEqualTo(status)
        return responseBody
    }
}
