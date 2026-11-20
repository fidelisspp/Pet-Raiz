package com.raizpet.api.entity

/**
 * Tipo de pet do produto (decisão 3, docs/db/alteracoes-etapa2.sql). Os nomes são os valores
 * do ENUM no MySQL, por isso ficam em minúsculas (mesmo padrão de ProducerStatus no service-usuarios).
 */
@Suppress("EnumEntryName")
enum class PetType {
    dog, cat, both;

    companion object {
        /** null/vazio = sem filtro; valor desconhecido = null também, quem chama decide o erro. */
        fun deTexto(valor: String?): PetType? =
            valor?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
