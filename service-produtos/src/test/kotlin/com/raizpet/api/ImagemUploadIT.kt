package com.raizpet.api

import com.raizpet.api.web.dto.ImagemResponseDto
import com.raizpet.api.web.dto.ProdutoCreateDto
import com.raizpet.api.web.dto.ProdutoCriadoResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Consumer

/**
 * POST /api/v1/produtos/meus/imagens (multipart, campo "arquivo").
 * Nos testes os arquivos vão para target/test-uploads/images (application-test.properties).
 */
class ImagemUploadIT : IntegracaoBase() {

    private val png: ByteArray = ClassPathResource("static/images/teste-petraiz.png").contentAsByteArray
    private val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(100)
    private val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8 ".toByteArray() + ByteArray(100)

    private fun upload(
        bytes: ByteArray?,
        nome: String = "foto.png",
        tipo: MediaType = MediaType.IMAGE_PNG,
        campo: String = "arquivo",
        auth: Consumer<HttpHeaders>? = Tokens.produtorCarlos()
    ) = testClient.post()
        .uri("/api/v1/produtos/meus/imagens")
        .apply { if (auth != null) headers(auth) }
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(LinkedMultiValueMap<String, Any>().apply {
            if (bytes != null) {
                // Cada parte do multipart tem os próprios headers: Content-Disposition (campo + nome do arquivo) e Content-Type
                val parteHeaders = HttpHeaders().apply {
                    contentType = tipo
                    setContentDispositionFormData(campo, nome)
                }
                add(campo, HttpEntity(object : ByteArrayResource(bytes) {
                    override fun getFilename() = nome
                }, parteHeaders))
            } else {
                add("outro", "sem arquivo")
            }
        })
        .exchange()

    private fun urlDoUpload(bytes: ByteArray, nome: String, tipo: MediaType): String =
        upload(bytes, nome, tipo)
            .expectStatus().isCreated
            .expectBody(ImagemResponseDto::class.java).returnResult().responseBody!!.url

    private fun esperarArquivoInvalido(spec: org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec, motivo: String) {
        val erro = esperarErro(spec, 422)
        assertThat(erro.errors).containsEntry("arquivo", motivo)
    }

    @Test
    fun enviarImagem_PngValido_SalvarComUuidEServirEmImages() {
        val url = urlDoUpload(png, "minha foto.png", MediaType.IMAGE_PNG)

        // Nome = UUID; o nome original do cliente é descartado
        assertThat(url).matches("^/images/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png$")
        assertThat(Files.exists(Paths.get("target/test-uploads").resolve(url.removePrefix("/")))).isTrue()

        // Já é servido na mesma URL das fotos do seed, sem token
        val servido = testClient.get().uri(url).exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.IMAGE_PNG)
            .expectBody(ByteArray::class.java).returnResult().responseBody
        assertThat(servido).isEqualTo(png)
    }

    @Test
    fun enviarImagem_JpgEWebp_ExtensaoVemDoConteudo() {
        // Tipo decidido pelos bytes: JPEG enviado com nome .jpeg sai como .jpg
        assertThat(urlDoUpload(jpg, "foto.jpeg", MediaType.IMAGE_JPEG)).endsWith(".jpg")
        assertThat(urlDoUpload(webp, "foto.webp", MediaType.parseMediaType("image/webp"))).endsWith(".webp")
    }

    @Test
    fun enviarImagem_DuasVezes_GerarNomesDiferentes() {
        assertThat(urlDoUpload(png, "a.png", MediaType.IMAGE_PNG))
            .isNotEqualTo(urlDoUpload(png, "a.png", MediaType.IMAGE_PNG))
    }

    @Test
    fun enviarImagem_DepoisCadastrarProdutoComAUrl_ProdutoUsaAFoto() {
        val url = urlDoUpload(png, "foto.png", MediaType.IMAGE_PNG)

        val produto = testClient.post().uri("/api/v1/produtos")
            .headers(Tokens.produtorCarlos())
            .contentType(MediaType.APPLICATION_JSON)
            .body(ProdutoCreateDto(name = "Produto com foto", price = BigDecimal("10.00"), stock = 1, imageUrl = url, categoryId = 20))
            .exchange()
            .expectStatus().isCreated
            .expectBody(ProdutoCriadoResponseDto::class.java).returnResult().responseBody!!
        assertThat(produto.imageUrl).isEqualTo(url)
    }

    @Test
    fun enviarImagem_ExatamenteNoLimiteDe2MB_Aceitar() {
        val bytes = png + ByteArray(2 * 1024 * 1024 - png.size)
        upload(bytes).expectStatus().isCreated
    }

    @Test
    fun enviarImagem_AcimaDe2MB_RetornarErrorMessageStatus422() {
        val bytes = png + ByteArray(2 * 1024 * 1024 - png.size + 1)
        esperarArquivoInvalido(upload(bytes), "deve ter no máximo 2 MB")
    }

    @Test
    fun enviarImagem_TipoNaoAceitoOuDisfarcado_RetornarErrorMessageStatus422() {
        val msg = "deve ser uma imagem jpg, jpeg, png ou webp"
        // Texto com nome e Content-Type de JPEG: recusado pelo conteúdo
        esperarArquivoInvalido(upload("não sou imagem".toByteArray(), "foto.jpg", MediaType.IMAGE_JPEG), msg)
        // GIF de verdade: tipo fora da lista
        esperarArquivoInvalido(upload("GIF89a".toByteArray() + ByteArray(20), "foto.gif", MediaType.IMAGE_GIF), msg)
        // Executável com nome de imagem
        esperarArquivoInvalido(upload("MZ".toByteArray() + ByteArray(50), "foto.png", MediaType.IMAGE_PNG), msg)
    }

    @Test
    fun enviarImagem_ArquivoVazioOuAusente_RetornarErrorMessageStatus422() {
        val msg = "envie uma imagem no campo arquivo"
        esperarArquivoInvalido(upload(ByteArray(0)), msg)
        esperarArquivoInvalido(upload(null), msg)
        // Arquivo em outro campo
        esperarArquivoInvalido(upload(png, campo = "foto"), msg)
    }

    @Test
    fun enviarImagem_ComJsonEmVezDeMultipart_RetornarErrorMessageStatus415() {
        esperarErro(
            testClient.post().uri("/api/v1/produtos/meus/imagens")
                .headers(Tokens.produtorCarlos())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"arquivo":"x"}""")
                .exchange(),
            415
        )
    }

    @Test
    fun enviarImagem_SemToken_RetornarErrorMessageStatus401() {
        esperarErro(upload(png, auth = null), 401)
    }

    @Test
    fun enviarImagem_ComClienteOuAdmin_RetornarErrorMessageStatus403() {
        esperarErro(upload(png, auth = Tokens.clienteBia()), 403)
        esperarErro(upload(png, auth = Tokens.adminAna()), 403)
    }
}
