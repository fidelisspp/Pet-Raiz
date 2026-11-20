package com.raizpet.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/** Tudo que começa com /images/ é público. O arquivo de teste fica em src/test/resources/static/images/. */
class ImagensIT : IntegracaoBase() {

    @Test
    fun buscarImagem_SemToken_RetornarArquivoComStatus200() {
        val bytes = testClient.get().uri("/images/teste-petraiz.png")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.IMAGE_PNG)
            .expectBody(ByteArray::class.java)
            .returnResult().responseBody
        assertThat(bytes).isNotEmpty
    }

    @Test
    fun buscarImagemPadraoSemFoto_SemToken_RetornarArquivoComStatus200() {
        testClient.get().uri("/images/sem-foto.png")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.IMAGE_PNG)
    }

    @Test
    fun buscarImagem_Inexistente_RetornarErrorMessageStatus404() {
        esperarErro(testClient.get().uri("/images/nao-existe.jpg").exchange(), 404)
    }
}
