package com.raizpet.api.util


object DocumentoUtils {

    fun formatarCpf(cpf: String): String {
        val d = cpf.filter(Char::isDigit)
        require(d.length == 11) { "CPF deve ter 11 dígitos" }
        return "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
    }

    fun formatarCnpj(cnpj: String): String {
        val d = cnpj.filter(Char::isDigit)
        require(d.length == 14) { "CNPJ deve ter 14 dígitos" }
        return "${d.substring(0, 2)}.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-${d.substring(12)}"
    }
}
