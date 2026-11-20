package com.raizpet.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringDocOpenApiConfig {

    companion object {
        // Nome referenciado por @SecurityRequirement(name = "security") nos endpoints protegidos
        const val SECURITY_SCHEME = "security"
    }

    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .components(Components().addSecuritySchemes(SECURITY_SCHEME, securityScheme()))
            .info(
                Info()
                    .title("REST API - Pet Raiz - service-pedidos")
                    .description("Endereços, carrinho e pedidos do cliente (perfil CUSTOMER). Produtos e estoque vêm do " +
                        "service-produtos; a situação do cliente (suspenso) vem do service-usuarios.")
                    .version("v1")
                    .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
                    .contact(Contact().name("Equipe Pet Raiz - PI Senac"))
            )

    private fun securityScheme(): SecurityScheme =
        SecurityScheme()
            .description("Insira um bearer token valido (emitido pelo service-usuarios) para prosseguir")
            .type(SecurityScheme.Type.HTTP)
            .`in`(SecurityScheme.In.HEADER)
            .scheme("bearer")
            .bearerFormat("JWT")
            .name(SECURITY_SCHEME)
}
