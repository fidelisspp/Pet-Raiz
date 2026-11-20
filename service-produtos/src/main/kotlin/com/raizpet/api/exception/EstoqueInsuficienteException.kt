package com.raizpet.api.exception

/**
 * Reserva recusada (409): algum item sem estoque suficiente ou produto inativo.
 * [itens]: productId -> motivo, para o service-pedidos saber quais itens falharam.
 */
class EstoqueInsuficienteException(val itens: Map<String, String>) :
    RuntimeException("Reserva recusada: estoque insuficiente ou produto indisponível. Nenhum item foi baixado.")
