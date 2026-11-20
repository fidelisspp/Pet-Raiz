package com.raizpet.api.service

import com.raizpet.api.exception.ParametroInvalidoException
import com.raizpet.api.repository.PurchaseOrderRepository
import com.raizpet.api.web.dto.ClienteComprasDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/** Visão do ADMIN sobre os pedidos (Etapa 5.3: resumo por cliente; a 5.5 acrescenta a lista e as ações). */
@Service
class PedidoAdminService(private val purchaseOrderRepository: PurchaseOrderRepository) {

    companion object {
        const val MAX_IDS = 100
    }

    /** Um item por id pedido, na mesma ordem; quem não tem pedido pago vem com 0 e 0.00. */
    @Transactional(readOnly = true)
    fun comprasPorCliente(ids: List<Int>): List<ClienteComprasDto> {
        val unicos = ids.distinct()
        if (unicos.isEmpty()) throw ParametroInvalidoException("Informe ao menos um id em ids")
        if (unicos.size > MAX_IDS) throw ParametroInvalidoException("No máximo $MAX_IDS ids por consulta")
        val resumo = purchaseOrderRepository.resumirPagosPorCliente(unicos).associate { linha ->
            (linha[0] as Number).toInt() to ((linha[1] as Number).toLong() to (linha[2] as BigDecimal))
        }
        return unicos.map { id ->
            val (pedidos, total) = resumo[id] ?: (0L to BigDecimal.ZERO)
            ClienteComprasDto(id, pedidos, total.setScale(2))
        }
    }
}
