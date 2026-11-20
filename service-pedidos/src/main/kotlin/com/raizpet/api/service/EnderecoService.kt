package com.raizpet.api.service

import com.raizpet.api.entity.Address
import com.raizpet.api.exception.ConflitoException
import com.raizpet.api.exception.EntityNotFoundException
import com.raizpet.api.repository.AddressRepository
import com.raizpet.api.repository.PurchaseOrderRepository
import com.raizpet.api.web.dto.EnderecoDto
import com.raizpet.api.web.dto.mapper.EnderecoMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Endereços do cliente. Todas as operações filtram pelo customerId (id do token):
 * endereço de outro cliente responde 404, como se não existisse.
 */
@Service
class EnderecoService(
    private val addressRepository: AddressRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository
) {

    @Transactional(readOnly = true)
    fun listar(customerId: Int): List<Address> = addressRepository.findAllByCustomerIdOrderByIdAsc(customerId)

    @Transactional(readOnly = true)
    fun buscar(id: Int, customerId: Int): Address =
        addressRepository.findByIdAndCustomerId(id, customerId)
            ?: throw EntityNotFoundException("Endereço id=$id não encontrado")

    @Transactional
    fun criar(dto: EnderecoDto, customerId: Int): Address =
        addressRepository.save(EnderecoMapper.copiar(dto).apply { this.customerId = customerId })

    /**
     * O pedido guarda só o address_id (não copia o endereço). Se o PUT fosse permitido, um pedido já
     * feito (inclusive o que o produtor está enviando) passaria a mostrar outro endereço. Por isso
     * endereço já usado em pedido não pode ser alterado: 409. Para entregar em outro lugar, o cliente
     * cadastra um endereço novo.
     */
    @Transactional
    fun atualizar(id: Int, dto: EnderecoDto, customerId: Int): Address {
        val address = buscar(id, customerId)
        bloquearSeUsadoEmPedido(id, "alterado")
        return EnderecoMapper.copiar(dto, address)
    }

    /**
     * O pedido aponta para o endereço (fk_purchase_order_address), então endereço já usado em pedido
     * não pode ser apagado: 409.
     */
    @Transactional
    fun excluir(id: Int, customerId: Int) {
        val address = buscar(id, customerId)
        bloquearSeUsadoEmPedido(id, "excluído")
        addressRepository.delete(address)
    }

    private fun bloquearSeUsadoEmPedido(id: Int, acao: String) {
        if (purchaseOrderRepository.existsByAddressId(id)) {
            throw ConflitoException("Endereço id=$id já foi usado em um pedido e não pode ser $acao")
        }
    }
}
