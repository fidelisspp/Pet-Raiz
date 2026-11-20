package com.raizpet.api.exception

/**
 * Regra de negócio que impede a operação (409). [itens] (opcional) vai no campo errors do
 * ErrorMessage: productId -> motivo. Ex.: quantidade acima do estoque, reserva recusada pelo
 * service-produtos, endereço usado em pedido, pagamento já confirmado.
 */
class ConflitoException(message: String, val itens: Map<String, String>? = null) : RuntimeException(message)

/** Checkout com o carrinho vazio (422). */
class CarrinhoVazioException : RuntimeException("O carrinho está vazio: adicione itens antes de finalizar o pedido")

/** Cliente suspenso no service-usuarios (ou que não existe lá como cliente): 403 no checkout. */
class ClienteBloqueadoException(message: String) : RuntimeException(message)

/** Dependência (service-produtos ou service-usuarios) fora do ar ou respondendo errado: 503. */
class ServicoIndisponivelException(message: String) : RuntimeException(message)

/**
 * A reserva de estoque deu certo, mas a transação local do pedido falhou (500). O estoque já foi
 * estornado (ou, se nem o estorno funcionou, ficou registrado no log de erro para correção manual).
 */
class PedidoNaoCriadoException(message: String) : RuntimeException(message)

/** Parâmetro de consulta inválido (ex.: ?ids= vazio ou com mais de 100): vira 400. */
class ParametroInvalidoException(message: String) : RuntimeException(message)
