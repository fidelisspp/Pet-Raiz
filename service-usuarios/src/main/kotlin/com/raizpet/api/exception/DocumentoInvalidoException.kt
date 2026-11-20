package com.raizpet.api.exception

/** Documento do cadastro recusado (ausente, vazio ou grande demais): vira 422 com errors.documento. */
class DocumentoInvalidoException(motivo: String) : CampoInvalidoException("documento", motivo, "Documento inválido")
