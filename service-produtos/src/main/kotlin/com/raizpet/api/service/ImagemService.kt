package com.raizpet.api.service

import com.raizpet.api.exception.ArquivoInvalidoException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Grava as fotos enviadas pelo produtor em <petraiz.uploads.dir>/images/<uuid>.<ext>.
 * A mesma pasta está em spring.web.resources.static-locations, então o arquivo já sai em GET /images/<uuid>.<ext>.
 *
 * O tipo é decidido pelos primeiros bytes do arquivo ("assinatura"), e não pelo nome ou pelo
 * Content-Type enviados pelo cliente: esses dois o cliente escolhe à vontade.
 */
@Service
class ImagemService(@Value("\${petraiz.uploads.dir}") uploadsDir: String) {

    companion object {
        const val TAMANHO_MAXIMO = 2L * 1024 * 1024
        const val MSG_TIPO = "deve ser uma imagem jpg, jpeg, png ou webp"
        const val MSG_TAMANHO = "deve ter no máximo 2 MB"
    }

    private val log = LoggerFactory.getLogger(ImagemService::class.java)

    private val pasta: Path = Paths.get(uploadsDir, "images").toAbsolutePath().normalize()

    init {
        Files.createDirectories(pasta)
        log.info("Uploads de imagens em {}", pasta)
    }

    /** Devolve o caminho relativo que vai no imageUrl do produto, ex.: /images/3f2a...e1.jpg */
    fun salvar(arquivo: MultipartFile?): String {
        if (arquivo == null || arquivo.isEmpty) throw ArquivoInvalidoException("envie uma imagem no campo arquivo")
        if (arquivo.size > TAMANHO_MAXIMO) throw ArquivoInvalidoException(MSG_TAMANHO)

        val bytes = arquivo.bytes
        val extensao = extensaoPelaAssinatura(bytes) ?: throw ArquivoInvalidoException(MSG_TIPO)

        // UUID: nome imprevisível e sem colisão; o nome original do cliente é descartado (evita ../ e afins)
        val nome = "${UUID.randomUUID()}.$extensao"
        Files.write(pasta.resolve(nome), bytes, StandardOpenOption.CREATE_NEW)
        log.info("Imagem salva: {} ({} bytes)", nome, bytes.size)
        return "/images/$nome"
    }

    private fun extensaoPelaAssinatura(b: ByteArray): String? = when {
        // JPEG: FF D8 FF
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "jpg"
        // PNG: 89 'P' 'N' 'G' 0D 0A 1A 0A
        b.size >= 8 && b.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        ) -> "png"
        // WEBP: "RIFF" ???? "WEBP"
        b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
    }
}
