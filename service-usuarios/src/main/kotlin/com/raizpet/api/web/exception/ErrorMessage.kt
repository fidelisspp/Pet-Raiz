package com.raizpet.api.web.exception

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult

class ErrorMessage @JsonCreator constructor(
    val path: String? = null,
    val method: String? = null,
    val status: Int = 0,
    val statusText: String? = null,
    val message: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val errors: Map<String, String>? = null
) {

    constructor(request: HttpServletRequest, status: HttpStatus, message: String?) : this(
        path = request.requestURI,
        method = request.method,
        status = status.value(),
        statusText = status.reasonPhrase,
        message = message
    )

    constructor(request: HttpServletRequest, status: HttpStatus, message: String?, result: BindingResult) : this(
        path = request.requestURI,
        method = request.method,
        status = status.value(),
        statusText = status.reasonPhrase,
        message = message,
        errors = result.fieldErrors.associate { it.field to (it.defaultMessage ?: "") }
    )

    override fun toString(): String =
        "ErrorMessage(path=$path, method=$method, status=$status, statusText=$statusText, message=$message, errors=$errors)"
}
