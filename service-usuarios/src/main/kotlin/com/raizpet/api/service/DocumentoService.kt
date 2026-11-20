package com.raizpet.api.service

import com.raizpet.api.exception.DocumentoInvalidoException
import com.raizpet.api.exception.EntityNotFoundException
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
 * Grava o documento do cadastro do produtor em <petraiz.uploads.dir>/documentos/<uuid>.
 * Qualquer formato é aceito (decisão da Etapa 5.1); só o tamanho é limitado.
 * A pasta NÃO está em static-locations: o arquivo só sai pela rota do admin.
 */
@Service
class DocumentoService(@Value("\${petraiz.uploads.dir}") uploadsDir: String) {

    companion object {
        const val TAMANHO_MAXIMO = 5L * 1024 * 1024
        const val MSG_AUSENTE = "envie o documento de regularidade no campo documento"
        const val MSG_TAMANHO = "deve ter no máximo 5 MB"
    }

    private val log = LoggerFactory.getLogger(DocumentoService::class.java)

    private val pasta: Path = Paths.get(uploadsDir, "documentos").toAbsolutePath().normalize()

    init {
        Files.createDirectories(pasta)
        log.info("Documentos de produtores em {}", pasta)
    }

    fun validar(arquivo: MultipartFile?) {
        if (arquivo == null || arquivo.isEmpty) throw DocumentoInvalidoException(MSG_AUSENTE)
        if (arquivo.size > TAMANHO_MAXIMO) throw DocumentoInvalidoException(MSG_TAMANHO)
    }

    /** Salva e devolve o nome gerado (UUID). O nome original do cliente nunca vira caminho (evita ../ e afins). */
    fun salvar(arquivo: MultipartFile): String {
        validar(arquivo)
        val nome = UUID.randomUUID().toString()
        Files.write(pasta.resolve(nome), arquivo.bytes, StandardOpenOption.CREATE_NEW)
        log.info("Documento salvo: {} ({} bytes)", nome, arquivo.size)
        return nome
    }

    fun ler(nome: String): ByteArray {
        val arquivo = pasta.resolve(nome).normalize()
        if (!arquivo.startsWith(pasta) || !Files.exists(arquivo)) {
            throw EntityNotFoundException("Arquivo do documento não encontrado")
        }
        return Files.readAllBytes(arquivo)
    }
}
