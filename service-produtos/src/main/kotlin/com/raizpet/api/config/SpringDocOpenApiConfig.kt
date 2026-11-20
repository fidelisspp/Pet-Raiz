package com.raizpet.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OpenApiCustomizer
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

        // Nome referenciado nas rotas /api/v1/interno/** (header X-Internal-Key)
        const val INTERNO_SCHEME = "interno"
    }

    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .components(
                Components()
                    .addSecuritySchemes(SECURITY_SCHEME, securityScheme())
                    .addSecuritySchemes(INTERNO_SCHEME, internoScheme())
            )
            .info(
                Info()
                    .title("REST API - Pet Raiz - service-produtos")
                    .description("Catálogo público de produtos e categorias, área do produtor (cadastro, edição, remoção e " +
                        "upload de foto), estoque (rotas internas) e imagens em /images")
                    .version("v1")
                    .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
                    .contact(Contact().name("Equipe Pet Raiz - PI Senac"))
            )

    /**
     * Toda rota protegida por token documenta o 401 (token ausente, inválido ou expirado). Em vez de
     * repetir o @ApiResponse em cada endpoint, este customizador acrescenta o 401 onde faltar e deixa
     * as respostas em ordem de código. Rotas públicas (sem security) não são alteradas.
     */
    @Bean
    fun respostas401(): OpenApiCustomizer = OpenApiCustomizer { api ->
        api.paths?.values?.flatMap { it.readOperations() }?.forEach { op ->
            val respostas = op.responses ?: return@forEach
            if (!op.security.isNullOrEmpty() && !respostas.containsKey("401")) {
                respostas.addApiResponse(
                    "401",
                    ApiResponse()
                        .description("Token ausente, inválido ou expirado")
                        .content(Content().addMediaType("application/json", MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ErrorMessage"))))
                )
                op.responses = ApiResponses().apply { respostas.toSortedMap().forEach { (codigo, r) -> addApiResponse(codigo, r) } }
            }
        }
    }

    private fun securityScheme(): SecurityScheme =
        SecurityScheme()
            .description("Insira um bearer token valido (emitido pelo service-usuarios) para prosseguir")
            .type(SecurityScheme.Type.HTTP)
            .`in`(SecurityScheme.In.HEADER)
            .scheme("bearer")
            .bearerFormat("JWT")
            .name(SECURITY_SCHEME)

    private fun internoScheme(): SecurityScheme =
        SecurityScheme()
            .description("Chave entre serviços (petraiz.interno.chave). Não é usada pelo app.")
            .type(SecurityScheme.Type.APIKEY)
            .`in`(SecurityScheme.In.HEADER)
            .name(InternoKeyFilter.HEADER)
}
