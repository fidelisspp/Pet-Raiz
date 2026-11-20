package com.raizpet.api

import com.raizpet.api.web.dto.CategoriaResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference

class CategoriaIT : IntegracaoBase() {

    private fun listar(): List<CategoriaResponseDto> {
        val responseBody = testClient
            .get()
            .uri("/api/v1/categorias")
            .exchange()
            .expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<CategoriaResponseDto>>() {})
            .returnResult().responseBody
        assertThat(responseBody).isNotNull
        return responseBody!!
    }

    @Test
    fun listarCategorias_SemToken_RetornarTodasEmOrdemAlfabeticaComStatus200() {
        assertThat(listar()).containsExactly(
            CategoriaResponseDto(20, "Petiscos"),
            CategoriaResponseDto(10, "Ração Natural")
        )
    }

    @Test
    fun listarCategorias_ComToken_RetornarStatus200() {
        testClient
            .get()
            .uri("/api/v1/categorias")
            .headers(Tokens.clienteBia())
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun listarCategorias_ComServicoDeUsuariosComErro_ContinuaFuncionandoESemChamarOServico() {
        UsuariosStub.modo = UsuariosStub.Modo.ERRO_500
        assertThat(listar()).hasSize(2)
        assertThat(UsuariosStub.chamadas.get()).isZero()
    }
}
