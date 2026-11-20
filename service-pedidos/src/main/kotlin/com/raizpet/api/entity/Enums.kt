package com.raizpet.api.entity

import java.math.BigDecimal

/*
 * Os nomes das constantes são os valores dos ENUMs no MySQL, por isso ficam em minúsculas
 * (mesmo padrão de PetType no service-produtos e ProducerStatus no service-usuarios).
 */

/** Frete único por pedido, com os valores fixos da tela (decisão 1 de alteracoes-etapa2.sql). */
@Suppress("EnumEntryName")
enum class ShippingMethod(val amount: BigDecimal) {
    standard(BigDecimal("12.90")),
    express(BigDecimal("24.90"))
}

/** Novo = pending, Em preparo = preparing, Enviado = shipped, Entregue = delivered (decisão 6). */
@Suppress("EnumEntryName")
enum class DeliveryStatus { pending, preparing, shipped, delivered }

/**
 * No banco é 'credit_card' | 'pix'. Na API o app manda e recebe "card" | "pix" ([api]).
 */
@Suppress("EnumEntryName")
enum class PaymentMethod(val api: String) {
    credit_card("card"),
    pix("pix");

    companion object {
        fun deApi(valor: String?): PaymentMethod? = entries.firstOrNull { it.api == valor }
    }
}

@Suppress("EnumEntryName")
enum class PaymentStatus { pending, paid, failed, refunded }
